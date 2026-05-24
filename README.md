# FreeMarket Mod - NeoForge 1.21.1

## 環境
- Windows 11 + Docker Desktop + VS Code DevContainer
- Java 21 / NeoForge 21.1.172 / Minecraft 1.21.1

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

### 3. ビルド（コンテナ内）
```bash
./gradlew build

# キャッシュ起因の問題が出た場合はクリーンから
rm -rf ~/.gradle/caches/ build/ .gradle/
./gradlew build
```

### 4. ゲーム起動（注意）
DevContainer内からMinecraftクライアントは起動不可（GUI非対応）。  
**ビルドのみDevContainerで実施** → 生成された `build/libs/*.jar` をホスト側の  
`.minecraft/mods/` にコピーして通常のMinecraftランチャーで起動。

```bash
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
│   ├── FreeMarketMod.java               # メインクラス・イベント登録
│   ├── ModItems.java                    # 日本円コイン
│   ├── ModMenuTypes.java                # GUIメニュー登録（フリマ・オークション）
│   ├── auction/
│   │   ├── AuctionListing.java          # 出品データ（入札・期限管理）
│   │   ├── AuctionMenu.java             # オークションコンテナメニュー
│   │   ├── AuctionSavedData.java        # オークションデータ永続化
│   │   └── AuctionTickHandler.java      # 落札処理・全員sync（100tick毎）・流札返却/破棄・自動再出品
│   ├── command/
│   │   ├── MarketCommand.java           # /market open|balance|give
│   │   └── AuctionCommand.java          # /auction open
│   ├── client/
│   │   ├── AuctionScreen.java           # オークションGUI（一覧/入札タブ・出品タブ・期間選択UI）
│   │   ├── FleaMarketScreen.java        # フリマGUI（アイコン表示・カテゴリフィルタ）
│   │   ├── ClientNetworkHandler.java    # クライアント側パケット処理
│   │   └── ItemCategory.java           # アイテムカテゴリ動的判定（武器/防具/道具/食料/ブロック/その他）
│   ├── data/
│   │   └── MarketSavedData.java         # 残高・出品・ボーナス・未渡しアイテムキュー管理（永続化）
│   ├── event/
│   │   └── PlayerLoginHandler.java      # 初回ログインボーナス付与・未渡しアイテム配送
│   ├── market/
│   │   ├── MarketListing.java           # フリマ出品データ
│   │   ├── FleaMarketMenu.java          # フリマコンテナメニュー
│   │   └── MobListingGenerator.java     # モブ自動出品（ワールドロード時・落札/流札/購入後の自動補充）
│   └── network/
│       ├── ModNetwork.java              # パケット登録・ハンドラ（入札通知・フリマ購入後自動補充）
│       ├── MarketPackets.java           # 旧パケット定義（後方互換）
│       └── payload/
│           ├── OpenMarketPayload.java   # S→C: フリマ画面を開く
│           ├── OpenAuctionPayload.java  # S→C: オークション画面を開く
│           ├── SyncListingsPayload.java # S→C: フリマ出品一覧同期（itemId含む）
│           ├── SyncAuctionPayload.java  # S→C: オークション出品一覧同期（入札履歴・itemId含む）
│           ├── BuyPayload.java          # C→S: フリマ購入
│           ├── SellPayload.java         # C→S: フリマ出品
│           ├── BidPayload.java          # C→S: オークション入札
│           └── SellAuctionPayload.java  # C→S: オークション出品（開始価格・出品期間）
├── build.gradle
├── gradle.properties
└── settings.gradle
```

---

## コマンド一覧

| コマンド | 説明 |
|---------|------|
| `/market open` | フリーマーケットGUIを開く |
| `/market balance` | 現在の残高を表示 |
| `/market give <金額>` | 残高を付与（デバッグ用） |
| `/auction open` | オークションGUIを開く |

---

## 通貨仕様

| 操作 | 方法 |
|------|------|
| 初回ボーナス | 初回ログイン時に ¥10,000 自動付与（チャット通知あり） |
| 残高確認 | `/market balance` |
| 残高付与（デバッグ）| `/market give <金額>` |
| フリマ購入 | フリマGUIで「購入」ボタン |
| フリマ出品 | フリマGUIでアイテムを手に持って「出品」ボタン → 価格入力 |
| オークション入札 | オークションGUIで金額入力 → 「入札する」ボタン |
| オークション出品 | オークションGUI「出品」タブで価格・期間（3分/30分/1時間）を選択 → 「出品する」ボタン |
| オークション落札（オンライン） | 期限切れ時にチャット通知＋インベントリに直接付与 |
| オークション落札（オフライン） | 次回ログイン時に自動配送＋チャット通知 |

---

## セーブデータ

| ファイル | 内容 |
|---------|------|
| `saves/<ワールド名>/data/freemarket_data.dat` | フリマ出品・残高・ボーナス受取済みUUID・未渡しアイテムキュー |
| `saves/<ワールド名>/data/freemarket_auctions.dat` | オークション出品・入札履歴 |

> 動作確認時にリセットしたい場合は両ファイルを削除して新ワールドを作成。

---

## 設計メモ（NeoForge 1.21.1 確定API）

```java
// SavedData
SavedData.save(CompoundTag, HolderLookup.Provider)
SavedData.Factory<T> + computeIfAbsent(FACTORY, NAME)

// ItemStack
ItemStack.save(registries)
ItemStack.parseOptional(registries, tag)

// ネットワーク
RegisterPayloadHandlersEvent / PayloadRegistrar
IPayloadContext

// Tick
LevelTickEvent.Post
```

---

## 実装フェーズ

### ✅ Phase 1: 環境 + データ基盤
- Devcontainer構築
- SavedData（出品・残高）
- モブ初期出品ロジック

### ✅ Phase 2: フリーマーケットGUI
- FleaMarketScreen（一覧・購入・出品）
- ネットワークパケット（SyncListings・Buy・Sell）
- コマンド `/market open|balance|give`

### ✅ Phase 3: オークション基盤
- AuctionListing（入札・終了時刻・入札履歴）
- AuctionSavedData（NBT永続化）
- AuctionScreen（一覧・入札UI）
- AuctionTickHandler（100tick毎の落札処理）

### ✅ Phase 4: 統合・動作確認
- `/auction open` コマンド追加
- 初回ログインボーナス ¥10,000（PlayerLoginHandler）
- モブ自動出品：フリマ8件・オークション4件（ワールドロード時）
- GUIぼかし（被写界深度エフェクト）解消
- 落札後の全プレイヤーへのオークションデータ再同期

### ✅ Phase 5: オフライン落札者への未渡しキュー
- MarketSavedData に `pendingItems`（Map<UUID, List<ItemStack>>）を追加・NBT永続化
- AuctionTickHandler: オフライン落札者をキューへ登録、モブ出品の流札は破棄
- PlayerLoginHandler: ログイン時に未渡しアイテムを自動配送・チャット通知
- オークション期間を3分に変更（`AUCTION_DURATION_MS = 3 * 60 * 1000L`）

### ✅ Phase 6: オークション改善
- **自動再出品**: 落札・流札でモブ出品が減った際、`AuctionTickHandler` の処理後に `MobListingGenerator.replenishAuctionIfNeeded()` を呼び出して不足分を補充
- **カウントダウン表示**: `AuctionDto.endTimeMs`（絶対時刻）を `render()` 内で毎フレーム `System.currentTimeMillis()` と差分計算しており、追加対応なしでリアルタイム更新済みと確認
- **入札履歴GUI表示**: `SyncAuctionPayload.AuctionDto` に `List<BidHistoryEntry>` を追加してDTO転送、`AuctionScreen` でホバー時にツールチップ表示（新しい順・最大5件・「X秒前」形式）

### ✅ Phase 7: GUI改善・通知
- **フリマ自動再出品**: 購入後に `MobListingGenerator.replenishMarketIfNeeded()` を呼び出し、モブ出品が8件を下回った場合に自動補充
- **入札チャット通知**: 入札成功時に全プレイヤーへ `[オークション] プレイヤー名 が アイテム名 に ¥X,XXX で入札しました` を送信
- **アイテムアイコン表示**: `SyncListingsPayload` / `SyncAuctionPayload` の DTO に `itemId`（レジストリキー）を追加。フリマ・オークション画面の各行に `GuiGraphics.renderItem()` で16x16アイコンを描画

### 🔜 Phase 8: 未実装・改善候補
- 出品期限・カテゴリフィルタ（変更範囲大：DTO・GUI・保存データ）

### ✅ Phase 8: カテゴリフィルタ・オークション出品期限

**カテゴリフィルタ（フリマ・オークション）**
- 方式: 動的判定（DTOもSavedDataも変更なし）
- `ItemCategory.java` 新規追加: ItemStack からカテゴリを判定（武器/防具/道具/食料/ブロック/その他）
- 食料判定: `stack.has(DataComponents.FOOD)`（`isEdible()` は1.21.1で廃止）
- 斧は `DiggerItem` 判定により「道具」に分類
- フリマ・オークション画面上部にタブUIを追加

**オークション出品期限（プレイヤー出品）**
- `SellAuctionPayload.java` 新規追加: 開始価格 + 出品期間（durationMs）を送信
- `AuctionScreen` に「出品」タブを追加: 手持ちアイテムプレビュー・価格入力・期間選択ボタン（3分/30分/1時間）
- 期間選択: `§a§l▶` で選択中を緑強調表示
- サーバー側バリデーション: 不正な durationMs は 3分にフォールバック
- `AuctionListing` のコンストラクタ `(sellerUUID, sellerName, stack, startPrice, durationMs)` を流用。endTimeMs への変換はコンストラクタ内で完結
- 流札挙動: プレイヤー出品 → 出品者に返却（オンライン: 直接付与、オフライン: pendingItems キュー）/ モブ出品 → 従来通り破棄
- `AuctionTickHandler` は既に流札返却ロジック実装済みにつき変更なし

### 🔜 Phase 9: 改善候補
- フリマのカテゴリフィルタタブの動作確認・調整
- オークション出品一覧への「出品期間」列追加
- 残高不足時の出品ブロック（出品時に最低入札額分を仮ロック）