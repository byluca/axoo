# Licenze e attribuzioni

La [GNU Affero General Public License v3.0](LICENSE), identificata come
`AGPL-3.0-only`, si applica al codice originale del client Axoo.
Il codice e le risorse di terze parti mantengono le condizioni dei rispettivi autori.
Le dipendenze Gradle sono dichiarate in `gradle/libs.versions.toml`.

- **Telegram rlottie**: sorgenti in `app/src/main/cpp/lottie_engine`; provenienza in
  [avvisi del motore](app/src/main/cpp/lottie_engine/TELEGRAM_PROVENANCE.md). Il `LICENSE` importato dal repository Telegram contiene la GPL v2.
  I sorgenti del motore mantengono i rispettivi avvisi, compresi quelli LGPL 2.1
  e, dove indicato, le versioni successive. Sono stati identificati gli
  originali di [lottiekeypath.cpp](https://github.com/Samsung/rlottie/blob/b2ded242de05ccf2c17756f7efcdf621759b6bfb/src/lottie/lottiekeypath.cpp)
  e [vimageloader.h](https://github.com/Samsung/rlottie/blob/3db9cd3b15bd8ada2a1c49bc9cb8ac05929871f4/src/vector/vimageloader.h)
  nelle revisioni LGPL 2.1 di Samsung. La copia iOS è identica e quella
  Android aggiunge solo un commento iniziale. Per le parti del fork coperte
  dalla licenza [GPL v2 o successiva dichiarata da Telegram](https://telegram.org/apps#telegram-for-android)
  si applica l'opzione [GPL v3](app/src/main/cpp/lottie_engine/licenses/COPYING.GPL-3.0) quando
  sono combinate con Axoo. La combinazione con l'AGPL v3 è prevista dalla
  sezione 13. I file con licenze specifiche mantengono i rispettivi avvisi.
  I testi dei sottocomponenti sono nella cartella `licenses` del motore.
  Le copie distribuite con l'app sono in `app/src/main/assets/licenses/TelegramRLottie`.
- **Inter e Baloo 2**: SIL Open Font License 1.1. Testi completi in
  `app/src/main/assets/licenses/Inter-OFL.txt` e `Baloo2-OFL.txt`.
- **Avatar Adventurer**: Lisa Wischofsky, CC BY 4.0, come indicato nei metadati SVG.
  [Fonte originale](https://www.figma.com/community/file/1184595184137881796) e
  [attribuzione DiceBear](https://www.dicebear.com/styles/adventurer/).
  Gli avatar completi usano l'API DiceBear.
- **comment_sent.wav**: derivato da “Message Sent” di Froey_, Freesound 760370,
  distribuito con CC0 1.0. Tagliato, regolato di volume e convertito
  in WAV PCM mono 48 kHz a 16 bit.
  [Fonte](https://freesound.org/people/Froey_/sounds/760370/).

Logo, marchi, icone e animazioni Axooji sono risorse originali del progetto.
I diritti su queste risorse restano al titolare salvo diversa indicazione.
