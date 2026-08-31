# OuterTune

[![OuterTune app icon](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/astertune.png)](https://github.com/yuuichi-s/AsterTune/blob/dev/assets/astertune.png)


[![Latest release](https://img.shields.io/github/v/release/yuuichi-s/AsterTune?include_prereleases)](https://github.com/yuuichi-s/AsterTune/releases)
[![License](https://img.shields.io/github/license/yuuichi-s/AsterTune)](https://www.gnu.org/licenses/gpl-3.0)

[English](README.md) | [日本語](README_ja.md)

Android向け Material 3 YouTube Music クライアント & ローカル音楽プレイヤー

> [!NOTE]
> これは [OuterTune/OuterTune](https://github.com/OuterTune/OuterTune) をベースにしたフォークです。
>
> - 現時点では配布手段を用意していませんが、今後用意する可能性があります。
>
> 利用したい場合は、ご自分でビルドできます。多くの方には `core` ビルドをおすすめします。
>
> ```bash
> # core debug build
> ./gradlew assembleCoreDebug
> ```
>
> 詳しい手順は [CONTRIBUTING.md](https://github.com/yuuichi-s/AsterTune/blob/dev/CONTRIBUTING.md) をご覧ください。

## このフォークで改善していること

このフォークでは、[OuterTune/OuterTune](https://github.com/OuterTune/OuterTune) をベースに、YouTube Music の再生安定性、歌詞表示、操作性、ローカル音楽再生まわりを中心に改善しています。

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

### 内部ライブラリ・ビルド環境

- Kotlin、KSP、NewPipeExtractor、Ktor、Android Gradle Plugin、Gradle などを更新

## 機能

OuterTune は [InnerTune](https://github.com/z-huang/InnerTune) を強化したフォークです。ローカル音楽プレイヤーと YouTube Music クライアントの両機能を備えています。

- YouTube Music クライアント機能
    * 楽曲のダウンロード（オフライン再生）
    * 広告なし・バックグラウンド再生によるシームレスな再生
    * アカウント同期
        + アプリからリモートアカウントへのプレイリスト完全同期は現在一時的に利用不可
- ローカル音声ファイルの再生（MP3、OGG、FLACなど）
    * ローカル楽曲と YouTube Music の楽曲を同時に再生可能
    * MediaStore の壊れたメタデータ抽出器の代わりにカスタムタグ抽出器を使用（`\` 区切りのタグなども正しく表示）
- スタイリッシュな Material 3 デザイン
- 複数キュー
- 同期歌詞、および単語単位・カラオケ形式の歌詞に対応（LRC、TTMLなど）
- 音量正規化、テンポ・ピッチ調整、その他各種オーディオエフェクト
- Android Auto 対応
- Android 8（Oreo）以降をサポート

> [!NOTE]
> Android 8（Oreo）以降をサポートしています。Android 7.x（Nougat）でも動作する可能性はありますが、正式サポート対象外です。

## スクリーンショット

[![メインプレイヤー画面](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/main-interface.jpg)](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/main-interface.jpg)

[![プレイヤー画面](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/player.jpg)](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/player.jpg)

[![YouTube Music との同期](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/ytm-sync.jpg)](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/ytm-sync.jpg)

[全画像ギャラリー](https://github.com/yuuichi-s/AsterTune/tree/dev/assets/gallery)

> [!WARNING]
> YouTube Music が利用できない地域では、プロキシまたは VPN を使用しない限りこのアプリは使用できません。

## ビルド & コントリビュート

ご自身でビルドしたい方は[ビルドおよびコントリビュートに関するノート](CONTRIBUTING.md)をご覧ください。

### 翻訳の投稿

OuterTune の翻訳には Weblate を使用しています。詳細や翻訳の投稿は[Weblate ページ](https://hosted.weblate.org/projects/yuuichi-s-outertune/)をご覧ください。

[![翻訳ステータス](https://hosted.weblate.org/widget/yuuichi-s-outertune/multi-auto.svg)](https://hosted.weblate.org/projects/yuuichi-s-outertune/)

世界中の方々に OuterTune をお届けするためにご協力いただきありがとうございます。

## ヘルプ & サポート

- **このフォーク固有のバグ**については、[このリポジトリの Issue](https://github.com/yuuichi-s/AsterTune/issues) を作成してください。

## クレジット

すべてのコントリビューターに感謝します。[こちら](https://github.com/OuterTune/OuterTune/graphs/contributors)からご確認いただけます。

このフォークの素晴らしいベースを提供してくださった [z-huang/InnerTune](https://github.com/z-huang/InnerTune) なしには実現できませんでした。

ローカル音楽プレイヤーの理想的な体験のインスピレーションをくれた [Musicolet](https://play.google.com/store/apps/details?id=in.krosbits.musicolet)。

精神的サポートと伝説の歌詞パーサーを提供してくれた [Gramophone](https://github.com/FoedusProgramme/Gramophone)。

## 免責事項

本プロジェクトおよびその内容は、YouTube、Google LLC またはその関連会社・子会社と一切関係なく、資金提供、承認、推薦も受けていません。

本プロジェクトで使用されている商標、サービスマーク、商号、その他の知的財産権はそれぞれの権利者に帰属します。
