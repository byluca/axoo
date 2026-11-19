package main

import (
	"fmt"
	"math/rand/v2"
	"strings"
)

// Keep different styles balanced so a large batch doesn't become hundreds of
// copies of a handful of words with a numeric suffix.
var nicknameWords = strings.Fields(`
alambicco albicocca amaca ametista ananas aquilone arancia ardesia aurora avocado
balena bambu basilico biglia binario bollicina bonsai borraccia bradipo briciola
brina briscola broccolo calamita camaleonte cannella cannolo caramella carbonara
cardamomo carillon cartolina castagna cedrata cerbiatto ciliegia clementina clessidra
coccinella cocomero coriandolo cornetto cornetta corallo cotoletta crepuscolo crocchetta
dattero domino edera enigma espresso farfalla fagiolino falena fennec fiammifero
fiocco fogliolina formichina frisbee frullato fulmine galassia garofano gelsomino
girasole gocciola gomitolo granita gufo ibisco igloo ippocampo labirinto lampone
lanterna lavanda lenticchia libellula liquirizia lontra lucciola luna mandarino
mandorla mangrovia maracuja margherita marmellata medusa melagrana melanzana melodia
mentuccia merendina meteorite mimosa miraggio moka mirtillo molletta mosaico muffin
nastrino nebulosa ninfea nocciola nuvola oboe origami ortensia otaria paguro
pancake papavero papaya papillon paprika pastello patatina pavone pecorella peperone
pergamena petalo pettirosso piadina pianeta pistacchio pixel piuma polaroid polpetta
ponte porcospino portachiavi prisma quaderno quadrifoglio ravanello riccio risotto
rondine rosmarino rubino rugiada rullino salvia sassolino scintilla segnalibro
sesamo sfogliatella siluro solstizio stella strudel susina tamarindo tangerino
tartaruga tazzina teiera tempesta timballo timo trifoglio tulipano turchese uragano
velluto ventaglio vespa viola zafferano zampetta zenzero zibibbo zolletta zucchina
sottaceto tortellino provolone bufalotto sgorbietto scarabocchio pinguino pangolino
capibara bradipino panzerottino focaccina tarallino puccetta mozzarellina crostino
pistacchietto caciottina salamella ciabatta spaghetto grattachecca scrocchiarella
sbriciolino patapum caciocavallo pasticciaccio cetriolino sgabello calzino
`)

var nicknamePhrases = strings.Fields(`
oggi_non_studio quasi_laureato caffe_e_ansia arrivo_tra_poco ultimo_appello
ho_perso_il_bus solo_un_altro_esame domani_inizio pausa_infinita torno_a_letto
non_ho_la_penna mi_manca_un_cfu aula_sbagliata fuori_dal_corso senza_appunti
studio_domani cerca_un_passaggio sempre_in_ritardo ci_vediamo_in_mensa zero_sonno
tutto_sotto_ctrl va_bene_cosi gia_lunedi piove_sul_libretto voto_e_scappo
non_sento_la_sveglia torno_subito un_altro_caffe ho_finito_i_giga basta_slide
treno_in_ritardo chi_ha_gli_appunti libri_mai_aperti cfu_cercasi fame_da_sessione
sono_in_biblioteca chiuso_per_esami sopravvivo_a_caffe mercoledi_libero oggi_si_dorme
senza_caricatore perso_in_ateneo penna_blu cercasi_motivazione ultimo_banco
piano_b_sempre dove_sta_aula riparto_da_zero penso_alla_pizza mare_dopo_esame
scendo_in_centro coinquilino_fantasma pasta_a_mezzanotte torno_col_regionale ancora_cinque_min
notte_prima_esame sotto_la_coperta prof_non_mi_senta zaino_troppo_pieno registro_vuoto
appunti_a_caso fuori_sede_dentro un_posto_al_sole non_era_in_programma sveglia_alle_otto
ho_fame_sempre libro_in_prestito vento_sul_lungomare porto_le_focacce ci_penso_lunedi
ultimo_tentativo chi_prende_appunti oggi_niente_lezione aula_senza_prese dov_e_la_mensa
il_wifi_non_va pranzo_alle_quattro non_trovo_aula un_giro_in_piu zaino_e_speranze
sotto_sessione sono_solo_slide lezione_annullata bevo_e_ripasso niente_panico mi_siedo_in_fondo
quasi_puntuale vado_a_caso oggi_senza_audio fa_troppo_caldo chiamami_dopo ho_preso_diciotto
prendo_il_prossimo si_vede_domani perso_tra_le_slide passo_in_segreteria niente_spicci
sono_al_bar davanti_alla_porta piu_sonno_che_cfu tesi_in_alto_mare cambio_argomento
ansia_e_panzerotti cfu_in_fuga tesi_al_dente libretto_croccante caffe_senza_effetto
fuorisede_in_letargo studio_sottovuoto appunti_al_pesto sessione_da_asporto
laurea_in_pigiama bocciato_dal_wifi il_prof_mi_vede panico_in_dispensa
ho_studiato_il_menu mensa_con_lode dispensa_senza_senso matricola_al_forno
ripasso_col_cuscino esame_con_patate zaino_di_emergenze prenoto_e_sparisco
bus_perso_laurea_pure polpo_fuori_corso trenta_e_focaccia sono_solo_per_i_cfu
piango_sul_libretto torno_dopo_il_caffe oggi_solo_presenza cattedra_a_rotelle
un_cfu_alla_volta appello_del_cuscino laurea_a_bagnomaria il_badge_non_va
`)

var nicknameHandles = strings.Fields(`
altf4 apollo artemis astra atlas atomo azimut blip bloop bytewise calypso
cassiopea celestino cobalto cosmo ctrlz curiosity cygnus daphne daydream delta
echo eclisse electra epsilon equinox eris esc falco fantasma fiamma flashback
flipflop fruscio glitch grifone halley helios hydra hyperion icarus idra indaco
io iris jupiter kaizen karma kepler kometa kora laguna lambda lanterna lira
lofi lumen lyra marea meraki mercurio metis mezzaluna midori milkyway minerva
mochi moonbeam morganite morfeo naos nemo nettuno nexa nimbus nori nova
nyx odissea omega onice opale orion ottanio oversleep pandora pastis pegaso
phoenix ping pong pluto pollux pulsar quasar quokka rewind ritmo rocket ronin
sakura saturno selene senpai shiro sirio skye sloth slowmotion solaria sonata
spica spirale stardust synth talos tanuki taro tetris titan tofu totoro tritone
twilight ukiyo ulisse vega vento vespro vinile vortex wabi yoda yuki zenit zeta
pingopongo flopzilla crasholino lagghetto pixelotto nannabyte ctrlpizza tabaperta
errorino snackoverflow sonnoexe bitbiscotto focacciabyte glitchino capibarista
panzeronauta procrastinando gufonauta memerendo tastofuga
`)

var nicknameModifiers = strings.Fields(`
in_orbita senza_wifi in_pigiama al_bar in_fuga sotto_esame in_mensa senza_sonno
di_notte sul_treno al_sole in_vacanza in_pausa per_caso con_ansia a_caso
di_corsa in_aula nel_caos in_centro fuori_sede in_bici sotto_casa in_ritardo
in_terrazza al_mare coi_libri sulla_luna nel_frigo da_asporto in_loop
`)

func generateManagedNicknames(count int, existing []string) ([]string, error) {
	used := make(map[string]bool, len(existing)+count)
	for _, name := range existing {
		used[strings.ToLower(name)] = true
	}
	pools := [][]string{append([]string(nil), nicknameWords...), append([]string(nil), nicknamePhrases...), append([]string(nil), nicknameHandles...), {}}
	for _, word := range nicknameWords {
		for _, modifier := range nicknameModifiers {
			pools[3] = append(pools[3], word+"_"+modifier)
		}
	}
	for _, pool := range pools {
		rand.Shuffle(len(pool), func(i, j int) { pool[i], pool[j] = pool[j], pool[i] })
	}
	positions := make([]int, len(pools))
	styles := rand.Perm(len(pools))
	names := make([]string, 0, count)
	for len(names) < count {
		progress := false
		for _, style := range styles {
			for positions[style] < len(pools[style]) {
				name := strings.ReplaceAll(pools[style][positions[style]], "_", "")
				positions[style]++
				if used[strings.ToLower(name)] || !nicknamePattern.MatchString(name) {
					continue
				}
				used[strings.ToLower(name)] = true
				names = append(names, name)
				progress = true
				break
			}
			if len(names) == count {
				return names, nil
			}
		}
		if !progress {
			return nil, fmt.Errorf("nickname disponibili insufficienti")
		}
	}
	return names, nil
}
