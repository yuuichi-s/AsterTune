# AsterTune

[![AsterTune アプリアイコン](https://github.com/yuuichi-s/AsterTune/raw/dev/assets/astertune.png)](https://github.com/yuuichi-s/AsterTune/blob/dev/assets/astertune.png)


[![Latest release](https://img.shields.io/github/v/release/yuuichi-s/AsterTune?include_prereleases)](https://github.com/yuuichi-s/AsterTune/releases)
[![License](https://img.shields.io/github/license/yuuichi-s/AsterTune)](https://www.gnu.org/licenses/gpl-3.0)

[English](README.md) | [日本語](README_ja.md)

Android向け Material 3 YouTube Music クライアント & ローカル音楽プレイヤー

> [!NOTE]
> AsterTuneの準備中です。このREADMEは暫定的なものであり、移行が完了次第、書き直されます。

## このフォークについて

AsterTuneは[OuterTune/OuterTune](https://github.com/OuterTune/OuterTune) のフォークです。

APK配布のため、アプリケーション及びリポジトリ名称を OuterTune から AsterTune に変更しました。

メンテナンス作業完了後、APKのリリースをします。

今すぐアプリを使用したい場合は、ご自身でビルドしてください。
ほとんどの方には、`core` ビルドをお勧めします。
ALAC(.m4a)を再生する場合は、`full` ビルドをお勧めします。

```bash
# core デバッグビルド
./gradlew assembleCoreDebug

# full デバッグビルド
./gradlew assembleFullDebug
```

手順の詳細については、[CONTRIBUTING.md](https://github.com/yuuichi-s/AsterTune/blob/dev/CONTRIBUTING.md) をご覧ください。

### OuterTune から AsterTune へデータを移行する

別アプリになったため自動的なデータ移行が行われません。
お手数ですが、手動でバックアップと復元を行ってください。

バックアップには、ライブラリデータベースとアプリの設定が含まれています。
ダウンロードしたオーディオファイルは含まれていないため、復元後はダウンロードを再度行う必要があります。

1. OuterTune で **設定 → バックアップと復元** を開き、**バックアップ** をタップして、バックアップファイルを保存します。
2. AsterTuneをインストールします。
3. AsterTuneで、**設定 → バックアップと復元**を開き、**復元**をタップして、保存しておいたバックアップファイルを選択します。


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

## クレジット

すべてのコントリビューターに感謝します。[こちら](https://github.com/OuterTune/OuterTune/graphs/contributors)からご確認いただけます。

このフォークの素晴らしいベースを提供してくださった [z-huang/InnerTune](https://github.com/z-huang/InnerTune) なしには実現できませんでした。

ローカル音楽プレイヤーの理想的な体験のインスピレーションをくれた [Musicolet](https://play.google.com/store/apps/details?id=in.krosbits.musicolet)。

精神的サポートと伝説の歌詞パーサーを提供してくれた [Gramophone](https://github.com/FoedusProgramme/Gramophone)。

## 免責事項

本プロジェクトおよびその内容は、YouTube、Google LLC またはその関連会社・子会社と一切関係なく、資金提供、承認、推薦も受けていません。

本プロジェクトで使用されている商標、サービスマーク、商号、その他の知的財産権はそれぞれの権利者に帰属します。
