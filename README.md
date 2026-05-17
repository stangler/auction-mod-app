# FreeMarket Mod - NeoForge 1.21.1

## 環境
- Windows 11 + Docker Desktop + VS Code DevContainer
- Java 21 / NeoForge 21.1.77

---

## セットアップ手順

### 1. 前提ツール
```
Docker Desktop (Windows)  → https://www.docker.com/products/docker-desktop
VS Code                   → https://code.visualstudio.com
Dev Containers 拡張        → VS Code拡張でインストール
```

### 2. プロジェクト起動
```bash
# このフォルダをVS Codeで開く
code .

# 右下に「Reopen in Container」ポップアップ → クリック
# または Ctrl+Shift+P → "Dev Containers: Reopen in Container"
```

### 3. Gradle初期化（コンテナ内）
```bash
./gradlew genEclipseRuns   # Eclipse用（任意）
./gradlew genIntellijRuns  # IntelliJ用（任意）
./gradlew build            # ビルド確認
```

### 4. ゲーム起動（注意）
DevContainer内からMinecraftクライアントは起動不可（GUI非対応）。  
**ビルドのみDevContainerで実施** → 生成された `build/libs/*.jar` をホスト側の  
`.minecraft/mods/` にコピーして通常のMinecraftランチャーで起動。

```bash
# コンテナ内でビルド
./gradlew build

# 出力先
build/libs/freemarket-1.0.0.jar
```

---

## フォルダ構成
```
freemarket-mod/
├── .devcontainer/
│   ├── devcontainer.json
│   └── Dockerfile
├── src/main/java/com/example/freemarket/
│   ├── FreeMarketMod.java          # メインクラス
│   ├── ModItems.java               # 日本円コイン
│   ├── ModMenuTypes.java           # GUIメニュー登録
│   ├── data/
│   │   └── MarketSavedData.java    # ワールドデータ永続化
│   ├── market/
│   │   ├── MarketListing.java      # 出品データ
│   │   ├── FleaMarketMenu.java     # コンテナメニュー
│   │   └── MobListingGenerator.java # モブ自動出品
│   └── network/
│       └── MarketPackets.java      # 通信パケット
├── build.gradle
├── gradle.properties
└── settings.gradle
```

---

## 実装フェーズ

### ✅ Phase 1: 環境 + データ基盤
- Devcontainer構築
- SavedData（出品・残高）
- モブ初期出品ロジック

### 🔜 Phase 2: フリーマーケットGUI
- FleaMarketScreen（一覧・購入・出品）
- ネットワークパケット完成
- コマンド `/market open`

### 🔜 Phase 3: オークション
- AuctionListing（入札・終了時刻）
- AuctionSavedData
- AuctionScreen
- タイマーイベント（Tick）

---

## 通貨仕様

| 操作 | 方法 |
|------|------|
| 初期残高 | ワールド生成時 ¥10,000 付与 |
| 残高確認 | `/market balance` |
| 残高付与（デバッグ）| `/market give <金額>` |
| 購入 | フリマGUIで「購入」ボタン |
| 出品 | フリマGUIで「出品」ボタン → アイテム + 価格入力 |
