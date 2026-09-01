# AsterTune

**Android向けMaterial Design 3採用のYouTube Music クライアント & ローカルメディアプレイヤー**

<img src="https://github.com/yuuichi-s/AsterTune/raw/dev/assets/astertune.png" width="100" />


[![Latest release](https://img.shields.io/github/v/release/yuuichi-s/AsterTune?include_prereleases)](https://github.com/yuuichi-s/AsterTune/releases)
[![License](https://img.shields.io/github/license/yuuichi-s/AsterTune)](https://www.gnu.org/licenses/gpl-3.0)

[<img src="assets/badge_github.png" alt="GitHub で入手" height="40">](https://github.com/yuuichi-s/AsterTune/releases/latest)
<!--
[<img src="assets/badge_obtainium.png" alt="Obtainium で入手" height="40">](https://github.com/yuuichi-s/AsterTune/releases/latest)
[<img src="assets/badge_fdroid.svg" alt="F-Droid で入手" height="40">](https://f-droid.org/packages/io.github.yuuichi_s.astertune/)
[<img src="assets/IzzyOnDroidButtonGreyBorder.svg" alt="IzzyOnDroid で入手" height="40">](https://apt.izzysoft.de/fdroid/index/apk/io.github.yuuichi_s.astertune)
-->

[English](README.md) | [日本語](README_ja.md)

## 特徴

AsterTuneは、[OuterTune](https://github.com/OuterTune/OuterTune)をベースとするAndroidアプリケーションです。<br />
Material Design 3を採用し、ローカルメディアプレーヤーとYouTube Musicクライアントの両方の機能を備えてます。

OuterTuneのデータを移行する場合は[OuterTuneからの移行](#outertune-からの移行) をご覧ください。

## 機能

- YouTube Music
    - YouTube Musicの楽曲をシームレスに再生（広告なし & バックグラウンド再生）
    - YouTube Musicのアカウントに紐づくライブラリを同期
    - 楽曲のダウンロード（オフライン再生）

- ローカル音楽ファイル再生
    - 端末内の音声ファイル（MP3、OGG、FLAC等）の再生
    - タグの読み取りにMediaStoreではなく独自の抽出処理を使用し、複数値タグや特殊なタグも正しく読み取り
    - ローカルの曲とYouTube Musicの曲を同じキューで再生
    - Android Auto 対応

- 歌詞
    - 同期歌詞に対応。LRC や TTML など単語単位の形式も表示
    - LrcLib、KuGou、SimpMusic、BetterLyrics、YouTube の字幕トラックから歌詞を取得
    - 使用する取得元を選択可能。有効な取得元には並行して問い合わせ
    - 歌詞の取り込みと編集


## 移行手順（OuterTune → AsterTune）

アプリ内のバックアップ機能を使用することでデータを引き継ぐことが可能です。

> [!NOTE] 
> バックアップには、ライブラリデータベースとアプリの設定が含まれています。
> ダウンロードしたオーディオファイルは含まれていないため、復元後はダウンロードを再度行う必要があります。

1. OuterTune で **設定 → バックアップと復元** を開き、**バックアップ** をタップし、バックアップファイルを保存します。
2. AsterTuneで、**設定 → バックアップと復元**を開き、**復元**をタップし、保存しておいたバックアップファイルを選択します。


## このフォークで改善していること

<details>
<summary>OuterTune v0.10.1をベースに、YouTube Music の再生安定性、歌詞表示、操作性、ローカル音楽再生まわりを中心に改善しています。</summary>

### YouTube Music の再生・表示

- アルバムの楽曲が表示されない問題、プレイリスト表示時のクラッシュ、検索結果の取得失敗などを修正
- YouTube Music の再生を妨げる「Source error 2004」を解消
- サムネイル画像の解像度を改善
- プレイリストやアルバムを開く際、データの更新中に起こり得たクラッシュを修正
- m3u プレイリストの取り込み時のクラッシュを修正し、YouTube 楽曲との照合を改善

### 歌詞表示

- LrcLib とキャプショントラックを利用し、歌詞取得の精度と表示速度を改善
- 再生画面の操作バーに歌詞切替ボタンを追加
- SimpMusic と BetterLyrics を歌詞プロバイダーとして追加
- 有効なプロバイダーへタイムアウト付きで並列に問い合わせ
- 歌詞パネルを閉じている間も再生サービスで歌詞を取得

### アプリの操作とメニュー

- ボトムナビゲーションの挙動を調整し、タブ移動や再タップ時の動作を自然に変更
- フォルダー画面の検索バー、並び順、リスト更新の問題を修正
- タブ画面上部に常時表示していた検索バーを、検索・履歴・統計・設定などをまとめたアイコン列に変更
- ミニプレーヤーを左右にスワイプして前後の曲へ移動する操作を追加

### ローカル音楽再生

- ローカル楽曲のタグ読み取り、リンク処理、ギャップレス再生を改善
- アルバム画面に表示される楽曲数の誤りを修正
- 端末内の楽曲・アルバム・アーティスト・プレイリストを、絞り込みと検索で閲覧できる「ローカル」タブを追加

### 表示・設定

- タブレット向けUIを改善
- Android 14 以降ではシステムのコントラスト設定を自動検出
- カスタムアクセントカラーを追加
- 「オーディオフォーカスを維持」するプレイヤー設定を追加
- サインイン時、ホーム画面に YouTube Music の最近のアクティビティをカード表示で追加
- プレイヤーのキューを開くハンドルに現在のキュー名を表示
- アカウントアイコンを、サインイン中のアカウントのプロフィール画像に変更
- 設定画面を再編し、「外観」と「インターフェイス」を「外観と操作」に統合、「プライバシー」を最上位に追加

### 再生・ダウンロード

- フェードアウトして再生を完全に停止する睡眠タイマーを追加
- Wi-Fi 接続時のみダウンロードするトグルを追加

</details>

## スクリーンショット

### スマートフォン

| | | | |
|---|---|---|---|
| <img src="assets/gallery/homepage.png" height="480" /> | <img src="assets/gallery/player.png" height="480" /> | <img src="assets/gallery/lyrics.png" height="480" /> |  <img src="assets/gallery/queue_expanded.png" height="480" /> |
| ホーム | プレイヤー | 歌詞 | キュー |

### タブレット

| | |
|---|---|
| <img src="assets/gallery/tablet_home.png" height="240" /> | <img src="assets/gallery/tablet_lyrics_library_dark.png" height="240" /> | 
| ホーム | 歌詞ダーク | 
| <img src="assets/gallery/tablet_lyrics_library.png" height="240" /> | <img src="assets/gallery/tablet_queue_home_light.png" height="240" /> |
| 歌詞ライト  | キュー |

## 翻訳

AsterTuneの翻訳にはWeblateを使用しています。<br />
詳細や翻訳の投稿については、[Weblate page](https://hosted.weblate.org/projects/yuuichi-s-astertune/).

<a href="https://hosted.weblate.org/projects/yuuichi-s-astertune/">
<img src="https://hosted.weblate.org/widget/yuuichi-s-astertune/multi-auto.svg" alt="Translation status" />
</a>

## Credits

| Project | Discription |
|---|---|
| [OuterTune](https://github.com/OuterTune/OuterTune/) | Upstream project |
| [InnerTune](https://github.com/z-huang/InnerTune) | Original foundation of OuterTune |

## Special Thanks

| Project | Discription |
|---|---|
| [Musicolet](https://play.google.com/store/apps/details?id=in.krosbits.musicolet) | ローカルメディアプレーヤー体験 |
| [Gramophone](https://github.com/FoedusProgramme/Gramophone) | 歌詞パーサー |

## ソースからのビルド

```bash
# Core (.m4aが再生できません)
./gradlew assembleCoreDebug

# Full
./gradlew assembleFullDebug
```

手順の詳細は [CONTRIBUTING.md](CONTRIBUTING.md) をご覧ください。

> [!NOTE] 
> Makdownドキュメントは順次メンテナンスします。

## 免責事項

本プロジェクトおよびその内容は、YouTube、Google LLC またはその関連会社・子会社と一切関係なく、資金提供、承認、推薦も受けていません。

本プロジェクトで使用されている商標、サービスマーク、商号、その他の知的財産権はそれぞれの権利者に帰属します。
