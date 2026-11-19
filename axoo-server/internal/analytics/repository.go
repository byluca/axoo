package analytics

import (
	"time"

	"axoo-server/internal/referrals"
	"gorm.io/gorm"
)

type Repository struct {
	db  *gorm.DB
	now func() time.Time
}

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{db: db, now: time.Now}
}

func (r *Repository) RecordOpen(userID string) error {
	return r.upsertDailyActivity(userID, 0, 0, 0)
}

func (r *Repository) RecordAction(userID string, action Action) error {
	posts, comments, votes := 0, 0, 0
	switch action {
	case ActionPost:
		posts = 1
	case ActionComment:
		comments = 1
	case ActionVote:
		votes = 1
	default:
		return nil
	}
	return r.upsertDailyActivity(userID, posts, comments, votes)
}

func (r *Repository) upsertDailyActivity(userID string, posts, comments, votes int) error {
	return r.db.Exec(`
		INSERT INTO user_daily_activity AS activity (
			user_id, activity_date, university_id, first_seen_at, last_seen_at,
			posts_created, comments_created, votes_changed
		)
		SELECT
			u.id,
			(NOW() AT TIME ZONE 'Europe/Rome')::date,
			u.university_id,
			NOW(), NOW(), ?, ?, ?
		FROM users u
		WHERE u.id = ? AND u.is_admin = FALSE AND u.is_alpha_tester = FALSE
		ON CONFLICT (user_id, activity_date) DO UPDATE SET
			last_seen_at = NOW(),
			posts_created = activity.posts_created + EXCLUDED.posts_created,
			comments_created = activity.comments_created + EXCLUDED.comments_created,
			votes_changed = activity.votes_changed + EXCLUDED.votes_changed
	`, posts, comments, votes, userID).Error
}

func (r *Repository) Overview(days int, universityID string) (*Overview, error) {
	location, err := time.LoadLocation("Europe/Rome")
	if err != nil {
		return nil, err
	}
	today := r.now().In(location)
	startDate := today.AddDate(0, 0, -(days - 1)).Format(time.DateOnly)
	todayDate := today.Format(time.DateOnly)
	wauStart := today.AddDate(0, 0, -6).Format(time.DateOnly)
	mauStart := today.AddDate(0, 0, -29).Format(time.DateOnly)

	result := &Overview{
		GeneratedAt:  r.now().UTC(),
		PeriodDays:   days,
		UniversityID: universityID,
		Campuses:     []CampusSummary{},
	}

	usersQuery := r.db.Table("users AS u").Where(realUserScope("u"))
	if universityID != "" {
		usersQuery = usersQuery.Where("u.university_id = ?", universityID)
	}
	var totalUsers, newUsers int64
	if err := usersQuery.Count(&totalUsers).Error; err != nil {
		return nil, err
	}
	newUsersQuery := usersQuery.Where("(u.created_at AT TIME ZONE 'Europe/Rome')::date BETWEEN ? AND ?", startDate, todayDate)
	if err := newUsersQuery.Count(&newUsers).Error; err != nil {
		return nil, err
	}

	activityQuery := r.db.Table("user_daily_activity AS a").
		Joins("JOIN users u ON u.id = a.user_id").
		Where(realUserScope("u")).
		Where("a.activity_date BETWEEN ? AND ?", startDate, todayDate)
	if universityID != "" {
		activityQuery = activityQuery.Where("a.university_id = ?", universityID)
	}
	if err := activityQuery.Select(`
		COUNT(DISTINCT a.user_id) AS active_users,
		COUNT(DISTINCT a.user_id) FILTER (
			WHERE (u.created_at AT TIME ZONE 'Europe/Rome')::date >= ?
		) AS new_active_users,
		COUNT(DISTINCT a.user_id) FILTER (
			WHERE (u.created_at AT TIME ZONE 'Europe/Rome')::date < ?
		) AS returning_users,
		COUNT(DISTINCT a.user_id) FILTER (
			WHERE a.posts_created + a.comments_created + a.votes_changed > 0
		) AS engaged_users,
		COALESCE(SUM(a.posts_created), 0) AS posts_created,
		COALESCE(SUM(a.comments_created), 0) AS comments_created,
		COALESCE(SUM(a.votes_changed), 0) AS votes_changed
	`, startDate, startDate).Scan(&result.Summary).Error; err != nil {
		return nil, err
	}

	// One row per active user, regardless of the number of actions they performed.
	frequencyQuery := r.db.Table("user_daily_activity AS a").
		Joins("JOIN users u ON u.id = a.user_id").
		Where(realUserScope("u")).
		Where("a.activity_date BETWEEN ? AND ?", startDate, todayDate)
	if universityID != "" {
		frequencyQuery = frequencyQuery.Where("a.university_id = ?", universityID)
	}
	frequencyQuery = frequencyQuery.Select("a.user_id, COUNT(DISTINCT a.activity_date) AS active_days").Group("a.user_id")
	var frequency struct {
		RepeatUsers       int64
		ActiveDaysPerUser float64
	}
	if err := r.db.Table("(?) AS frequency", frequencyQuery).Select(`
		COUNT(*) FILTER (WHERE active_days >= 2) AS repeat_users,
		COALESCE(AVG(active_days), 0) AS active_days_per_user
	`).Scan(&frequency).Error; err != nil {
		return nil, err
	}
	result.Summary.RepeatUsers = frequency.RepeatUsers
	result.Summary.ActiveDaysPerUser = frequency.ActiveDaysPerUser

	reachQuery := r.db.Table("user_daily_activity AS a").
		Joins("JOIN users u ON u.id = a.user_id").
		Where(realUserScope("u")).
		Where("a.activity_date BETWEEN ? AND ?", mauStart, todayDate)
	if universityID != "" {
		reachQuery = reachQuery.Where("a.university_id = ?", universityID)
	}
	var reach struct {
		DAU            int64
		WAU            int64
		MAU            int64
		ReturningToday int64
	}
	if err := reachQuery.Select(`
		COUNT(DISTINCT a.user_id) FILTER (WHERE a.activity_date = ?) AS dau,
		COUNT(DISTINCT a.user_id) FILTER (WHERE a.activity_date >= ?) AS wau,
		COUNT(DISTINCT a.user_id) FILTER (
			WHERE a.activity_date = ? AND (u.created_at AT TIME ZONE 'Europe/Rome')::date < ?
		) AS returning_today,
		COUNT(DISTINCT a.user_id) AS mau
	`, todayDate, wauStart, todayDate, todayDate).Scan(&reach).Error; err != nil {
		return nil, err
	}
	// GORM Scan resets fields missing from the projection. Keep the three
	// independent aggregates separate so reach cannot erase users/actions.
	result.Summary.TotalUsers = totalUsers
	result.Summary.NewUsers = newUsers
	result.Summary.DAU = reach.DAU
	result.Summary.WAU = reach.WAU
	result.Summary.MAU = reach.MAU
	result.Summary.ReturningToday = reach.ReturningToday

	var dailyRows []DailyPoint
	dailyQuery := r.db.Table("user_daily_activity AS a").
		Joins("JOIN users u ON u.id = a.user_id").
		Where(realUserScope("u")).
		Where("a.activity_date BETWEEN ? AND ?", startDate, todayDate)
	if universityID != "" {
		dailyQuery = dailyQuery.Where("a.university_id = ?", universityID)
	}
	if err := dailyQuery.Select(`
		TO_CHAR(a.activity_date, 'YYYY-MM-DD') AS date,
		COUNT(DISTINCT a.user_id) AS active_users,
		COUNT(DISTINCT a.user_id) FILTER (
			WHERE (u.created_at AT TIME ZONE 'Europe/Rome')::date = a.activity_date
		) AS new_active_users,
		COUNT(DISTINCT a.user_id) FILTER (
			WHERE (u.created_at AT TIME ZONE 'Europe/Rome')::date < a.activity_date
		) AS returning_users,
		COUNT(DISTINCT a.user_id) FILTER (
			WHERE a.posts_created + a.comments_created + a.votes_changed > 0
		) AS engaged_users,
		COALESCE(SUM(a.posts_created), 0) AS posts_created,
		COALESCE(SUM(a.comments_created), 0) AS comments_created,
		COALESCE(SUM(a.votes_changed), 0) AS votes_changed
	`).Group("a.activity_date").Order("a.activity_date ASC").Scan(&dailyRows).Error; err != nil {
		return nil, err
	}
	result.Daily = fillDailyPoints(startDate, days, dailyRows)

	campusQuery := r.db.Table("users AS u").
		Select(`
			u.university_id,
			universities.name AS university_name,
			COUNT(DISTINCT u.id) AS total_users,
			COUNT(DISTINCT a.user_id) AS active_users,
			COUNT(DISTINCT a.user_id) FILTER (
				WHERE (u.created_at AT TIME ZONE 'Europe/Rome')::date < ?
			) AS returning_users,
			COUNT(DISTINCT a.user_id) FILTER (
				WHERE a.posts_created + a.comments_created + a.votes_changed > 0
			) AS engaged_users,
			COALESCE(SUM(a.posts_created + a.comments_created + a.votes_changed), 0) AS total_actions
		`, startDate).
		Joins("JOIN universities ON universities.id = u.university_id").
		Joins("LEFT JOIN user_daily_activity a ON a.user_id = u.id AND a.activity_date BETWEEN ? AND ?", startDate, todayDate).
		Where(realUserScope("u"))
	if universityID != "" {
		campusQuery = campusQuery.Where("u.university_id = ?", universityID)
	}
	if err := campusQuery.Group("u.university_id, universities.name").
		Order("active_users DESC, total_users DESC, universities.name ASC").
		Scan(&result.Campuses).Error; err != nil {
		return nil, err
	}
	result.Referrals, err = r.referralOverview(startDate, todayDate, universityID)
	if err != nil {
		return nil, err
	}

	return result, nil
}

// Count confirmed invitations on both sides, and only recorded rewards backed
// by real invitations. Alpha access never creates a reward row.
func (r *Repository) referralOverview(startDate, endDate, universityID string) (ReferralSummary, error) {
	var result ReferralSummary
	err := r.db.Raw(`
		WITH valid_referrals AS (
			SELECT r.inviter_id, r.invitee_id, r.created_at,
				inviter.university_id AS inviter_campus, invitee.university_id AS invitee_campus
			FROM referrals r
			JOIN users inviter ON inviter.id = r.inviter_id
			JOIN users invitee ON invitee.id = r.invitee_id
			WHERE r.is_test = FALSE
				AND `+referralUserScope("inviter")+`
				AND `+referralUserScope("invitee")+`
		), inviters AS (
			SELECT COUNT(DISTINCT inviter_id) AS total_inviters,
				COUNT(DISTINCT inviter_id) FILTER (
					WHERE (created_at AT TIME ZONE 'Europe/Rome')::date BETWEEN ? AND ?
				) AS period_inviters
			FROM valid_referrals WHERE (? = '' OR inviter_campus::text = ?)
		), invitees AS (
			SELECT COUNT(DISTINCT invitee_id) AS total_invitees,
				COUNT(DISTINCT invitee_id) FILTER (
					WHERE (created_at AT TIME ZONE 'Europe/Rome')::date BETWEEN ? AND ?
				) AS period_invitees
			FROM valid_referrals WHERE (? = '' OR invitee_campus::text = ?)
		), rewards AS (
			SELECT COUNT(*) AS total_unlocked_users,
				COUNT(*) FILTER (
					WHERE (p.granted_at AT TIME ZONE 'Europe/Rome')::date BETWEEN ? AND ?
				) AS period_unlocked_users
			FROM user_emoji_packs p
			JOIN users u ON u.id = p.user_id
			JOIN (SELECT inviter_id FROM valid_referrals GROUP BY inviter_id HAVING COUNT(*) >= ?) qualified
				ON qualified.inviter_id = u.id
			WHERE p.pack_id = ? AND (? = '' OR u.university_id::text = ?)
		)
		SELECT inviters.*, invitees.*, rewards.* FROM inviters CROSS JOIN invitees CROSS JOIN rewards
	`, startDate, endDate, universityID, universityID,
		startDate, endDate, universityID, universityID,
		startDate, endDate, referrals.Target, referrals.RewardPack, universityID, universityID).Scan(&result).Error
	return result, err
}

func referralUserScope(alias string) string {
	return realUserScope(alias) + " AND " + alias + ".email_hash NOT LIKE 'contentdesk-%' AND " + alias + ".email_hash NOT LIKE 'seed-%'"
}

// Analytics is sourced only from actual application accounts in users. An
// alpha invitation becomes a users row after login, so it is excluded by its
// durable flag rather than by joining the alpha_testers invitation table.
func realUserScope(alias string) string {
	return alias + ".is_admin = FALSE AND " + alias + ".is_alpha_tester = FALSE"
}

func fillDailyPoints(startDate string, days int, rows []DailyPoint) []DailyPoint {
	byDate := make(map[string]DailyPoint, len(rows))
	for _, row := range rows {
		byDate[row.Date] = row
	}
	start, _ := time.Parse(time.DateOnly, startDate)
	result := make([]DailyPoint, 0, days)
	for offset := 0; offset < days; offset++ {
		date := start.AddDate(0, 0, offset).Format(time.DateOnly)
		point := byDate[date]
		point.Date = date
		result = append(result, point)
	}
	return result
}
