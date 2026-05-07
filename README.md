# Codex Blanche（Android）

**日本語** | [English](README.en.md)

## 法務

- [利用規約](docs/TERMS_OF_SERVICE.md)（日本語） / [Terms of Service](docs/TERMS_OF_SERVICE.en.md)（English）
- [プライバシーポリシー](docs/PRIVACY_POLICY.md)（日本語） / [Privacy Policy](docs/PRIVACY_POLICY.en.md)（English）

---

[Codex Blanche](https://github.com/Tamari-log/Codex-Blanche) の **ネイティブ Android 版**です。Web アプリと同様、**Gemini** および **ChatGPT（OpenAI）** の API を使ったチャット体験を、端末上の Compose アプリとして提供します。データ形式や設定の考え方は Web 版に寄せてあり、バックアップのインポート／エクスポートや Google Drive 同期で **Web 版との併用**を想定した構成です。

| | リポジトリ |
|---|------------|
| **Web アプリ** | [Tamari-log/Codex-Blanche](https://github.com/Tamari-log/Codex-Blanche) |
| **本リポジトリ（Android）** | [Tamari-log/Codex-Blanche-App](https://github.com/Tamari-log/Codex-Blanche-App) |

## 主な機能

- **マルチプロバイダ**: Google Gemini / OpenAI（モデル・温度・最大トークン等は設定画面から変更可能）
- **セッション管理**: 複数チャット、ピン留め、セッション単位の上書き設定（`SessionOverrides`）
- **ペルソナ**: Web 版と同様の柔軟なプリセット（JSON 設定の互換を意識したデータ構造）
- **添付**: 画像などに加え、PDF / Office 系ドキュメントの取り込みに対応する処理（`AttachmentProcessor`）
- **バックアップ・同期**: チャットデータの JSON、`CodexBlanche` フォルダ名など Drive 連携用の設定（`DriveSyncRepository`）
- **見た目**: Web 版のトーンに合わせたテーマ（`CodexWebPalette` / `CodexWebTextures`）

アプリ ID: `com.tamarilog.codexblanche`  
最小 SDK: **26**（Android 8.0）／target / compile SDK: **34**

## 必要条件

- **JDK 17**
- **Android Studio**（推奨）または Android SDK 済みの環境
- 実機 / エミュレータから API へ到達できる **ネットワーク**
- API 利用には **Gemini API キー** および / または **OpenAI API キー**（設定画面で入力。端末内に保持するかはアプリ設定に依存）

## ビルドと実行

リポジトリルートで:

```bash
./gradlew assembleDebug
```

Windows:

```bash
gradlew.bat assembleDebug
```

生成されるデバッグ APK は `app/build/outputs/apk/debug/` に出力されます。開発時は Android Studio で `app` モジュールを開き、Run からデバイスへインストールするのが手軽です。

## プロジェクト構成（概要）

```
app/                    # Android アプリモジュール（Jetpack Compose）
├── src/main/java/...   # UI・ViewModel・API クライアント・データ層・Drive 同期など
└── src/main/res/       # リソース（テーマ・アイコン・文字列）
```

ルートの `settings.gradle.kts` ではプロジェクト名を `CodexBlanche` としています。

## Web 版との使い分け

- **ブラウザですぐ試したい・デプロイ済み環境で使う** → [Codex-Blanche](https://github.com/Tamari-log/Codex-Blanche)（Web）
- **オフライン寄りの操作感・通知・端末ストレージ・Play 配布を見据える** → 本リポジトリ（Android）

不具合や要望は、それぞれのリポジトリの Issues に報告してください。

---

*本 README は Web 版リポジトリの位置づけに合わせ、この Android リポジトリの役割とビルド手順を共有するためのものです。*
