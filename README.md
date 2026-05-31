# AuctionMod - NeoForge 1.21.1

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
build/libs/auctionmod-1.0.0.jar
```

---

## フォルダ構成
```
auction-mod/
├── .devcontainer/
│   ├── devcontainer.json
│   └── Dockerfile
├── src/main/java/com/example/auction/
│   ├── AuctionMod.java                  # メインクラス・イベント登録
│   ├── ModItems.java                    # 日本円コイン
│   ├── ModMenuTypes.java                # GUIメニュー登録（フリマ・オークション）
│   ├── auction/
│   │   ├── AuctionListing.java          # 出品データ（入札・期限管理）
│   │   ├── AuctionMenu.java             # オークションコンテナメニュー
│   │   ├── AuctionSavedData.java        # オークションデータ永続化
│   │   └── AuctionTickHandler.java      # 落札処理・全員sync（100tick毎）・流札返却/破棄・自動再出品・モブ落札処理
│   ├── command/
│   │   ├── MarketCommand.java           # /market open|balance|give
│   │   └── AuctionCommand.java          # /auction open
│   ├── client/
│   │   ├── AuctionScreen.java           # オークションGUI（一覧/入札タブ・出品タブ・期間選択UI・手数料プレビュー）
│   │   ├── FleaMarketScreen.java        # フリマGUI（出品一覧タブ・出品するタブ・カテゴリフィルタ・手持ちアイテムプレビュー・手数料プレビュー・自分の出品管理）
│   │   ├── ClientNetworkHandler.java    # クライアント側パケット処理・GUIエラーラベル表示
│   │   └── ItemCategory.java           # アイテムカテゴリ動的判定（武器/防具/道具/食料/ブロック/その他）
│   ├── data/
│   │   └── MarketSavedData.java         # 残高・出品・ボーナス・未渡しアイテムキュー管理（永続化）・手数料計算
│   ├── event/
│   │   └── PlayerLoginHandler.java      # 初回ログインボーナス付与・未渡しアイテム配送
│   ├── market/
│   │   ├── MarketListing.java           # フリマ出品データ
│   │   ├── FleaMarketMenu.java          # フリマコンテナメニュー
│   │   ├── MobListingGenerator.java     # モブ自動出品（ワールドロード時・落札/流札/購入後の自動補充）
│   │   ├── MobConstants.java            # モブ名定数・UUID生成・モブ判定（Phase 11）
│   │   └── MobBuyerScheduler.java       # モブ自動購入・入札スケジューラ（60秒毎・Phase 11）
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
│           ├── SellAuctionPayload.java  # C→S: オークション出品（開始価格・出品期間）
│           ├── CancelListingPayload.java  # C→S: フリマ出品取消（Phase 10）
│           ├── CancelAuctionPayload.java  # C→S: オークション出品取消（Phase 10）
│           └── ErrorMessagePayload.java   # S→C: GUIエラーラベル表示（Phase 10）
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
| フリマ出品 | フリマGUI「出品する」タブでアイテムを手に持って価格入力 → 「出品する」ボタン |
| オークション入札 | オークションGUIで金額入力 → 「入札する」ボタン |
| オークション出品 | オークションGUI「出品」タブで価格・期間（3分/30分/1時間）を選択 → 「出品する」ボタン |
| オークション落札（オンライン） | 期限切れ時にチャット通知＋インベントリに直接付与 |
| オークション落札（オフライン） | 次回ログイン時に自動配送＋チャット通知 |

---

## セーブデータ

| ファイル | 内容 |
|---------|------|
| `saves/<ワールド名>/data/auctionmod_data.dat` | フリマ出品・残高・ボーナス受取済みUUID・未渡しアイテムキュー |
| `saves/<ワールド名>/data/auctionmod_auctions.dat` | オークション出品・入札履歴 |

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

### ✅ Phase 8: カテゴリフィルタ・オークション出品期限

**カテゴリフィルタ（フリマ・オークション）**
- 方式: 動的判定（DTOもSavedDataも変更なし）
- `ItemCategory.java` 新規追加: ItemStack からカテゴリを判定（武器/防具/道具/食料/ブロック/その他）
- フリマ・オークション画面上部にタブUIを追加

**オークション出品期限（プレイヤー出品）**
- `SellAuctionPayload.java` 新規追加: 開始価格 + 出品期間（durationMs）を送信
- `AuctionScreen` に「出品」タブを追加: 手持ちアイテムプレビュー・価格入力・期間選択ボタン（3分/30分/1時間）
- サーバー側バリデーション: 不正な durationMs は 3分にフォールバック
- 流札挙動: プレイヤー出品 → 出品者に返却（オンライン: 直接付与、オフライン: pendingItems キュー）/ モブ出品 → 従来通り破棄

### ✅ Phase 9: カテゴリフィルタ調整・出品期間列・残高チェック

**フリマ カテゴリフィルタ組み込み（FleaMarketScreen）**
- `selectedCategory` フィールド追加・`getFilteredListings()` でフィルタ
- タブを手動描画（`rebuildWidgets` を避け `priceBox` 入力を保持）

**オークション一覧「出品期間」列追加**
- `AuctionListing` に `public final long durationMs` フィールド追加
- NBT save/load 対応（旧セーブデータはキーなし→0→`"―"` 表示でフォールバック）
- `SyncAuctionPayload.AuctionDto` に `durationMs` 追加（encode/decode/from 全対応）

**残高不足時の出品ブロック（フリマ・オークション）**
- サーバー側ハンドラ（`SellPayload` / `SellAuctionPayload`）で `getBalance()` チェックを追加
- 残高不足の場合はチャットにエラーメッセージを送信して処理を中断

### ✅ Phase 10: 出品取消・オークション上限・手数料・GUIエラーラベル

**出品取消**
- `CancelListingPayload` / `CancelAuctionPayload` 新規追加（UUID 1個を送信）
- `ModNetwork.java`: フリマ取消ハンドラ追加（本人確認 → `removeListing` → アイテム返却 → sync）
- `MarketSavedData.java`: `removeListing(UUID)` メソッド追加
- 取消制約: 入札済みオークションは取消不可（サーバー側 `hasBid()` で弾く）
- モブ出品は取消不可（サーバー側 `getSellerId()` / `sellerUUID` で本人確認）

**オークション出品上限（プレイヤーあたり3件まで）**
- `MarketPackets.handleSellAuction` に件数チェック追加
- 上限超過時: 「出品上限に達しています (上限: 3件)」メッセージ

**出品手数料**
- 定率5%・最低¥1・売却時徴収・流札時なし・モブ免除
- `MarketSavedData`: `calcFee()` / `isMobSeller()` 追加
- `AuctionTickHandler.settle()`: 落札額から手数料控除・オンライン出品者に通知
- フリマ・オークション画面に手数料プレビュー表示

**GUIエラーラベル**
- `ErrorMessagePayload` 新規追加（String message + int color）
- `ModNetwork.sendError()` helper追加（`COLOR_ERROR` / `COLOR_WARN` / `COLOR_SUCCESS`）
- 画面下部中央に80tick（4秒）フェードアウト表示

### ✅ Phase 11: モブ自動購入・入札

**概要**
- 村人A〜E・行商人・ウィッチ・略奪者・ピリジャーがNPCとして市場に参加
- 60秒毎にフリマ購入・オークション入札を自動実行

**新規ファイル**
- `MobConstants.java`: モブ名定数（9種）・`mobUUID()` / `isMobName()` を集約
- `MobBuyerScheduler.java`: 60秒毎スケジューラ（LevelTickEvent.Post）

**モブ残高**
- 初期残高: ¥10,000（全モブ共通）
- `MarketSavedData` の `balances` Map で管理（UUID = `nameUUIDFromBytes(name)`）
- フリマ購入時は残高消費、オークション落札時は `AuctionTickHandler` が控除

**フリマ購入ロジック**
- 60秒毎に30%確率で発火
- アクティブ出品からランダム1件選択
- 出品者本人モブは購入者候補から除外
- 残高足りるモブがいなければスキップ

**オークション入札ロジック**
- 60秒毎にアクティブ出品からランダム1件選択
- 入札額: `currentBid（またはstartPrice）× 最大150%` の範囲でランダム決定
- 出品者・現在最高入札者・残高不足モブを候補から除外
- 再入札: 次回スケジューラ起動時に自然に再抽選

**モブ落札処理（AuctionTickHandler 修正）**
- `isMobBidder(String)` 追加（`MobConstants.isMobName()` 使用）
- モブ落札時: 残高控除・アイテム破棄（ゲーム内に実体なし）・ログ出力
- プレイヤー落札時: 従来通り（オンライン直接付与・オフラインキュー）
### ✅ Phase 12: 出品時残高チェック撤廃

**バグ修正: 残高¥0でも出品可能に**
- `ModNetwork.java` (`SellPayload` ハンドラ) / `MarketPackets.java` (`handleSellAuction`) の `balance < 1` チェックを削除
- 手数料は売却時徴収の仕様であり、出品時に残高は不要
- Phase 9 で追加した残高チェックが仕様と矛盾していたため撤廃

### ✅ Phase 13: フリマ出品タブ分離

**フリマGUIに上位タブ（出品一覧 / 出品する）を追加**
- `FleaMarketScreen.java` のみ変更（サーバー側・パケット変更なし）
- `MainTab` enum追加（BROWSE / SELL）・`rebuildWidgets()` でタブ切替時にウィジェット再構築

**出品一覧タブ**
- 従来の一覧・購入・取消UIをそのまま収容
- カテゴリフィルタタブは出品一覧タブ内のみ表示

**出品するタブ**
- 手持ちアイテムプレビュー（アイコン・名前・数量をクライアント側で表示）
- 価格入力欄 + 手数料プレビュー（5%・最低¥1）
- 出品ボタン（手持ちスタック丸ごと・サーバー側取得は従来通り）
- 自分の出品中リスト（最大3件・取消ボタン付き）
- 下部固定出品エリアを廃止してタブ内に統合