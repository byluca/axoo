package main

import (
	"fmt"
	"net/http"
	"strings"
	"time"

	"axoo-server/internal/models"
	"github.com/google/uuid"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

const maxManagedProfiles = 300

type nicknameChange struct {
	ID          string
	OldNickname string
	Nickname    string
}

func (m managedProfiles) changeProfileNicknames(count int, since time.Time, repairOnly bool) ([]nicknameChange, error) {
	if count < 1 || count > maxManagedProfiles || since.IsZero() {
		return nil, fmt.Errorf("indica una data e da 1 a %d profili", maxManagedProfiles)
	}
	var changes []nicknameChange
	err := m.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended('contentdesk-profiles',0))").Error; err != nil {
			return err
		}
		var targets []profile
		if err := tx.Table("users").Select("id, nickname").Where("email_hash LIKE 'contentdesk-%' AND is_alpha_tester = TRUE AND is_admin = FALSE AND created_at >= ?", since).Order("created_at, id").Clauses(clause.Locking{Strength: "UPDATE"}).Scan(&targets).Error; err != nil {
			return err
		}
		if len(targets) != count {
			return fmt.Errorf("trovati %d profili, attesi %d: nessuna modifica", len(targets), count)
		}
		var existing []string
		if err := tx.Table("users").Pluck("nickname", &existing).Error; err != nil {
			return err
		}
		names, err := generateManagedNicknames(count, existing)
		if err != nil {
			return err
		}
		values := make([]string, 0, count)
		args := make([]any, 0, count*2)
		used := map[string]bool{}
		for _, name := range existing {
			used[strings.ToLower(name)] = true
		}
		for _, name := range names {
			used[name] = true
		}
		for i, p := range targets {
			name := names[i]
			if repairOnly {
				if nicknamePattern.MatchString(p.Nickname) {
					continue
				}
				preferred := strings.ToLower(strings.ReplaceAll(p.Nickname, "_", ""))
				if nicknamePattern.MatchString(preferred) && !used[preferred] {
					name = preferred
					used[name] = true
				}
			}
			changes = append(changes, nicknameChange{ID: p.ID, OldNickname: p.Nickname, Nickname: name})
			values = append(values, "(?::uuid, ?::text)")
			args = append(args, p.ID, name)
		}
		if len(changes) == 0 {
			return nil
		}
		result := tx.Exec("UPDATE users AS u SET nickname = v.nickname FROM (VALUES "+strings.Join(values, ",")+") AS v(id,nickname) WHERE u.id=v.id", args...)
		if result.Error != nil {
			return result.Error
		}
		if result.RowsAffected != int64(len(changes)) {
			return fmt.Errorf("conteggio aggiornamenti inatteso: rollback")
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	return changes, nil
}

func (d *desk) createProfiles(w http.ResponseWriter, r *http.Request) {
	var request struct {
		Gender       string `json:"gender"`
		Count        int    `json:"count"`
		UniversityID string `json:"university_id"`
	}
	if !decodeJSON(w, r, &request) {
		return
	}
	if request.Count < 1 || request.Count > maxManagedProfiles || !validProfileGender(request.Gender) {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("scegli da 1 a %d profili", maxManagedProfiles))
		return
	}
	if _, err := uuid.Parse(request.UniversityID); err != nil {
		writeError(w, http.StatusBadRequest, "campus non valido")
		return
	}
	var count int64
	if err := d.db.Table("universities").Where("id = ?", request.UniversityID).Count(&count).Error; err != nil {
		writeInternalError(w, err)
		return
	}
	if count != 1 {
		writeError(w, http.StatusBadRequest, "campus non trovato")
		return
	}
	created, err := d.profiles.addProfiles(request.Count, request.UniversityID, request.Gender)
	if err != nil {
		writeInternalError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, created)
}

func (m managedProfiles) addProfiles(count int, campusID, gender string) ([]profile, error) {
	if !validProfileGender(gender) {
		return nil, fmt.Errorf("categoria profilo non valida")
	}
	if count < 1 || count > maxManagedProfiles {
		return nil, fmt.Errorf("scegli da 1 a %d profili", maxManagedProfiles)
	}
	if _, err := uuid.Parse(campusID); err != nil {
		return nil, fmt.Errorf("campus non valido")
	}
	created := []profile{}
	err := m.db.Transaction(func(tx *gorm.DB) error {
		var campusName string
		if err := tx.Table("universities").Select("name").Where("id = ?", campusID).Scan(&campusName).Error; err != nil {
			return err
		}
		if campusName == "" {
			return fmt.Errorf("campus non trovato")
		}
		if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended('contentdesk-profiles',0))").Error; err != nil {
			return err
		}
		var existing []string
		if err := tx.Table("users").Pluck("nickname", &existing).Error; err != nil {
			return err
		}
		names, err := generateManagedNicknames(count, existing)
		if err != nil {
			return err
		}
		users := make([]models.User, 0, count)
		for _, name := range names {
			id := uuid.New()
			user := newContentDeskUser(id.String(), name, campusID, generatedAvatar(id))
			if gender != "" {
				user.EmailHash = "contentdesk-" + gender + "-" + id.String()
			}
			users = append(users, user)
			created = append(created, profile{ID: id.String(), Nickname: name, UniversityID: campusID, UniversityName: campusName, Gender: gender})
		}
		return tx.CreateInBatches(&users, 100).Error
	})
	if err != nil {
		return nil, err
	}
	return created, err
}

func validProfileGender(gender string) bool {
	return gender == "" || gender == "male" || gender == "female"
}
