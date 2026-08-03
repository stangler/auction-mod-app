# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Windows (PowerShell)
.\gradlew.bat build        # ビルド
.\gradlew.bat runClient    # クライアント起動（開発用）
.\gradlew.bat runServer    # サーバー起動（開発用）

# 配布用 jar → build/libs/auctionmod-1.0.0.jar
```

- **Java 25** / NeoForge 26.1.2.84 / Minecraft 26.1.2 / Gradle 9.6.1
- テストディレクトリは存在しない
- DevContainer は無効化済み（ローカル Java 25 ツールチェーンを使用）

## アーキテクチャ概要

### エントリポイント
`AuctionMod.java` — `@Mod("auctionmod")` のメインクラス。`IEventBus` 経由で Items / MenuTypes / Network を登録し、NeoForge イベントバスに `AuctionTickHandler`, `PlayerLoginHandler`, `MobBuyerScheduler` を登録。`RegisterCommandsEvent` で `/market` `/auction` コマンドを登録。

### ネットワークレイヤー

全通信は NeoForge 26.x の `CustomPacketPayload` + `PayloadRegistrar` 方式。

- **`ModNetwork.java`** — 全パケットの登録とサーバー側ハンドラを集約。S→C（open/sync/error）と C→S（buy/sell/bid/cancel）の両方を処理。sync処理とエラー送信のヘルパーを持つ。
- **`MarketPackets.java`** — 旧式のレコードベースのパケット定義（`ServerboundBuyPacket`）と、オークション系の拡張ハンドラ（`handleSellAuction`, `handleCancelAuction`）が残る。
- **`ClientNetworkHandler.java`** — クライアント専用ハンドラ。サーバーサイドクラスから `Minecraft.getInstance()` を呼ばないための分離層。`FleaMarketScreen` / `AuctionScreen` の表示切替と sync データの反映を行う。
- **`payload/*`** — 11のペイロード定義。各ペイロードは `CustomPacketPayload` + `StreamCodec<FriendlyByteBuf, T>` を実装し、DTO は内包の record で表現。

### データ永続化（Codec ベース）

NeoForge 26.x の `SavedDataType<T>` + `Codec<T>` 方式を使用（`CompoundTag` 方式ではない）。

- **`MarketSavedData`** — フリマ出品リスト(`Map<UUID, MarketListing>`)、残高(`Map<UUID, Long>`)、初回ボーナス済みフラグ(`Set<UUID>`)、未渡しアイテムキュー(`Map<UUID, List<ItemStack>>`) を永続化。Codec で全フィールドを serialize/deserialize。`MarketListing` の `ItemStack` は `ItemStack.CODEC` + `RegistryOps` で保存。
- **`AuctionSavedData`** — オークション出品リスト(`Map<UUID, AuctionListing>`) を永続化。Codec ベースだが ItemStack 保存に `RegistryOps`.

### ドメインモデル

- **`MarketListing`** — フリマ出品。`listingId(UUID)`, `sellerName/String`, `sellerId/UUID`, `itemStack`, `price(long)`, `sold(boolean)`。モブ出品は `sellerId == UUID.nameUUIDFromBytes(sellerName)` で判定。
- **`AuctionListing`** — オークション出品。加えて `startPrice`, `endTimeMs`, `currentBid`, `topBidderName`, `bidHistory(List<BidEntry>)`, `durationMs`。Codec と NBT 両方に対応（NBT は旧データ互換用に残る）。

### イベントハンドラ

- **`AuctionTickHandler`** — `LevelTickEvent.Post`（100 tick 毎、オーバーワールドのみ）: 期限切れオークションの落札/流札処理、モブオークション自動補充、全プレイヤー re-sync。
  - 落札先がオンライン → 直接インベントリ / オフライン → キュー
  - モブ落札: 残高控除のみ、アイテム破棄
  - 流札（モブ出品）→ 破棄 /（プレイヤー出品）→ 出品者に返却
- **`PlayerLoginHandler`** — 初回 ¥10,000 ボーナス + 未渡しアイテム配送
- **`MobBuyerScheduler`** — 60秒毎（1200tick）: モブ残高初期化、30%確率でフリマ購入、アクティブオークションにランダム入札

### モブシステム

- **`MobConstants`** — 9種類のモブ名（村人A~E, 行商人, ウィッチ, 略奪者, ピリジャー）。モブ UUID = `UUID.nameUUIDFromBytes(name)`
- **`MobListingGenerator`** — ワールドロード時にフリマ8件+オークション4件の初期出品。`replenishMarketIfNeeded()`, `replenishAuctionIfNeeded()` は各購入/落札後に呼ばれる
- **`MobBuyerScheduler`** — モブが定期的にフリマ購入とオークション入札を実行

### 手数料

- `MarketSavedData.calcFee(price)` — 5%・最低¥1
- 落札時に出品者から徴収（`AuctionTickHandler.settle()` 内）
- フリマ購入時: `purchase()` 内で計算
- モブ出品は手数料免除

### パケットフロー

```
C→S: SellPayload / SellAuctionPayload / BuyPayload / BidPayload / Cancel*Payload
S→C: OpenMarketPayload / OpenAuctionPayload / SyncListingsPayload / SyncAuctionPayload / ErrorMessagePayload
```

クライアント画面はすべて `FleaMarketScreen` / `AuctionScreen`。