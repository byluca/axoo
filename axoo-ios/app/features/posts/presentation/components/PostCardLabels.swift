extension Post {
    var authorLabel: String { nickname.isEmpty ? "Anonimo" : nickname }

    var metadata: String {
        let meta = [campus, timeLabel, isEdited ? "Modificato" : ""]
            .filter { !$0.isEmpty }.joined(separator: " · ")
        return meta.isEmpty ? "" : "· " + meta
    }
}
