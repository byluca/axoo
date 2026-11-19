package auth

import (
	"crypto/rand"
	"fmt"
	"math/big"
)

const nicknameNumberChoices = 10_000

// nicknameWords is intentionally made of lowercase ASCII words so nicknames
// stay a single, URL-safe word followed by a number from 0 through 9999.
var nicknameWords = []string{
	"panzerotto", "focaccia", "tarallo", "frisella", "orecchiette", "zeppola", "pasticciotto", "polpetta", "tramezzino", "popcorn",
	"girella", "bruschetta", "brioche", "cornetto", "sfogliatella", "gocciole", "ciambella", "anguria", "fragola", "ciliegia",
	"amarena", "cocco", "mango", "caramella", "amaretto", "marshmallow", "donut", "muffin", "waffle", "brownie",
	"cookie", "cupcake", "bonbon", "bubblegum", "gelato", "ghiacciolo", "granita", "pistacchio", "tiramisu", "cannolo",
	"peroni", "raffo", "dreher", "ceres", "heineken", "ichnusa", "primitivo", "negroamaro", "spritz", "negroni",
	"mojito", "gin", "vodka", "rum", "amaro", "borghetti", "sambuca", "tequila", "aperol", "campari",
	"crodino", "chinotto", "lemonsoda", "estathe", "prosecco", "limoncello", "mirto", "jager", "malibu", "redbull",
	"bigbabol", "kinderpingui", "happyhippo", "morositas", "galatina", "tictac", "cipster", "maxibon", "mikado", "fonzies",
	"goleador", "cucciolone", "tronky", "fiesta", "duplo", "bueno", "ringo", "smarties", "mentos", "fruitella",
	"calippo", "magnum", "solero", "viennetta", "oreo", "kitkat", "twix", "bounty", "pringles", "fruttolo",
	"burrata", "stracciatella", "mortadella", "porchetta", "crocchetta", "arancino", "pizzetta", "patatina", "biscotto", "peperoncino",
	"mozzarella", "scamorza", "bombetta", "capocollo", "salsiccia", "pancetta", "lasagna", "raviolo", "gnocco", "pesto",
	"carbonara", "parmigiana", "crostata", "pasticcino", "bigne", "profiterol", "pannacotta", "semifreddo", "sorbetto", "torroncino",
	"nocciola", "mandorla", "liquirizia", "meringa", "cheesecake", "tennents", "bacardi", "havana", "margarita", "caipirinha",
	"moscowmule", "cicchetto", "shottino", "birretta", "nutellago", "soda", "puccia", "pepsi", "cocacola", "fanta",
	"sprite", "schweppes", "sanbitter", "gingerino", "oransoda", "tassoni", "gazzosa", "cimedirapa", "ginlemon", "gintonic",
	"rumcola", "vodkalemon", "longisland", "moka", "corretto", "skittles", "haribo", "vigorsol", "daygum", "golia",
	"happydent", "frisk", "chupachups", "kinderbueno", "kindermaxi", "kinderdelice", "fettaallatte", "kinderparadiso", "kinderbrioss", "kinderjoy",
	"kindermerendero", "nutellabiscuits", "nutella", "loacker", "grisbi", "togo", "pavesini", "rustico", "milka", "galak",
	"maoam", "baci", "lindt", "rittersport", "cacioepepe", "danette", "amatriciana", "plumcake", "flauti", "tegolino",
	"baba", "bombolone", "cremino", "gianduiotto", "croissant", "crepes", "churros", "macaron", "cassata", "zabaione",
	"affogato", "tartufo", "croccantino", "gianduia", "nocciolata", "strudel", "maritozzo", "graffa", "mochi", "matcha",
	"pralina", "cioccolatino", "fondente", "mou", "tartufino", "wafer", "redvelvet", "cinnamonroll", "milkshake", "frappe",
	"montenegro", "cynar", "moretti", "nastroazzurro", "messina", "becks", "corona", "desperados", "guinness", "leffe",
	"menabrea", "baileys", "disaronno", "averna", "lucano", "camparisoda", "martini", "hugo", "paloma", "mimosa",
	"americano", "fernet", "espressomartini", "rossana", "jackdaniels", "jameson", "hendricks", "tanqueray", "jagerbomb", "vodkaredbull",
	"pandistelle", "baiocchi", "nascondini", "abbracci", "macine", "tarallucci", "trolli", "takis", "biscoff", "buondi",
	"soldino", "yoyo", "chocofresh", "kindercards", "sourpatch", "schokobons", "kindercountry", "nesquik", "mars", "snickers",
	"lion", "toblerone", "pocketcoffee", "rocher", "moncheri", "mms", "maltesers", "cocopops", "frosties", "chocapic",
	"krave", "cheerios", "lays", "doritos", "cheetos", "kindersorpresa", "algida", "nutellabready", "mcflurry", "babybel",
}

// generateNickname returns one immutable anonymous nickname in the form
// "word" + "number", e.g. "panzerotto67".
func generateNickname() (string, error) {
	wordIndex, err := rand.Int(rand.Reader, big.NewInt(int64(len(nicknameWords))))
	if err != nil {
		return "", fmt.Errorf("select nickname word: %w", err)
	}
	number, err := rand.Int(rand.Reader, big.NewInt(nicknameNumberChoices))
	if err != nil {
		return "", fmt.Errorf("select nickname number: %w", err)
	}
	return fmt.Sprintf("%s%d", nicknameWords[wordIndex.Int64()], number.Int64()), nil
}
