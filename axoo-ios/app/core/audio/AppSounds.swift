import AVFoundation

@MainActor
enum AppSounds {
    private static let comment: AVAudioPlayer? = {
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
        guard let url = Bundle.main.url(forResource: "comment_sent", withExtension: "wav", subdirectory: "sounds"),
              let player = try? AVAudioPlayer(contentsOf: url) else { return nil }
        player.volume = 0.30
        player.prepareToPlay()
        return player
    }()

    static func prepare() { _ = comment }
    static func commentSent() {
        comment?.currentTime = 0
        comment?.play()
    }
}
