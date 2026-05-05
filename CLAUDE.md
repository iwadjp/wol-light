# CLAUDE.md (wol-light)

## プロジェクト概要

- 名称: wol-light
- 場所: `D:\iwa\AI\Claude\maybe_public\wol-light`
- 種別: maybe_public（将来公開候補）
- 目的: Fing の WoL機能を代替する Android アプリ

親フォルダの共通ルールは `D:\iwa\AI\Claude\CLAUDE.md` を参照すること。

---

## 技術スタック

| 項目 | 選定 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose |
| 非同期 | Coroutines + Flow |
| DI | Hilt |
| DB | Room |
| 最小SDK | API 26 (Android 8.0) |
| ターゲットSDK | API 34 (Android 14) |

---

## 必要パーミッション (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 実装機能

### ① LAN スキャン（IP / MAC 取得）

- 現在のWiFiサブネットを自動検出（例: `192.168.1.0/24`）
- `192.168.x.1` 〜 `192.168.x.254` を Coroutines で並列スキャン
- 各IPに対し `InetAddress.isReachable(timeout = 300ms)` で生存確認
- 生存確認後、`ProcessBuilder("ip", "neigh", "show")` でARPテーブルを参照しMACアドレス取得を試みる
- ARPテーブルから取得できない場合はMAC欄を空白にし、後から手動入力できるようにする
- スキャン進捗をプログレスバーで表示（0〜254のカウント）

### ② デバイス一覧・選択

- スキャン結果をリスト表示（デバイス名 / IP / MAC）
- リストから任意のデバイスをタップして選択
- 選択したデバイスをRoom DBに登録・保存
- 登録済みデバイスは次回起動時も一覧に表示
- デバイスの編集（名前・MAC手動修正）・削除が可能

### ③ WoL 送信（Magic Packet）

- Magic Packet 仕様:
  - `0xFF` × 6バイト + MACアドレス × 16回 = 計102バイト
  - UDP ブロードキャスト（宛先: `255.255.255.255`、ポート: `9`）
- 送信後にSnackbarで成功/失敗を通知
- 送信は `Dispatchers.IO` でバックグラウンド実行

### ④ Ping 送信

- `InetAddress.getByName(ip).isReachable(2000)` でPing実行
- 結果（到達可能/不可、応答時間ms）をSnackbarで表示
- Ping は `Dispatchers.IO` でバックグラウンド実行

---

## ディレクトリ構成

```
wol-light/
├── app/
│   └── src/main/java/com/example/wol/
│       ├── MainActivity.kt
│       ├── di/
│       │   └── AppModule.kt
│       ├── data/
│       │   ├── db/
│       │   │   ├── AppDatabase.kt
│       │   │   ├── DeviceDao.kt
│       │   │   └── DeviceEntity.kt
│       │   └── repository/
│       │       └── DeviceRepository.kt
│       ├── domain/
│       │   └── usecase/
│       │       ├── LanScanUseCase.kt
│       │       ├── WolUseCase.kt
│       │       └── PingUseCase.kt
│       ├── network/
│       │   ├── IpScanner.kt
│       │   ├── ArpResolver.kt
│       │   ├── WolSender.kt
│       │   └── PingSender.kt
│       ├── ui/
│       │   ├── screen/
│       │   │   ├── DeviceListScreen.kt
│       │   │   ├── ScanScreen.kt
│       │   │   └── DeviceDetailScreen.kt
│       │   ├── viewmodel/
│       │   │   ├── DeviceListViewModel.kt
│       │   │   ├── ScanViewModel.kt
│       │   │   └── DeviceDetailViewModel.kt
│       │   └── navigation/
│       │       └── AppNavigation.kt
│       └── model/
│           └── Device.kt
└── CLAUDE.md（このファイル）
```

---

## 画面遷移

```
DeviceListScreen（起動画面）
  ├── [スキャンボタン] → ScanScreen
  │     └── [デバイス選択・登録] → DeviceListScreen（更新）
  └── [デバイスタップ] → DeviceDetailScreen
        ├── [WoL送信ボタン]
        ├── [Ping送信ボタン]
        └── [編集・削除]
```

---

## データモデル

```kotlin
data class Device(
    val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val macAddress: String,  // 空文字の場合は手動入力を促す
    val broadcastAddress: String = "255.255.255.255",
    val port: Int = 9
)
```

---

## 主要クラスの実装ガイド

### IpScanner.kt
```kotlin
// WifiManager から現在のIPとサブネットマスクを取得してサブネットを決定
// coroutineScope { (1..254).map { async { isReachable(ip) } }.awaitAll() }
// タイムアウト: 300ms
```

### ArpResolver.kt
```kotlin
// ProcessBuilder("ip", "neigh", "show").start() でARPテーブルを取得
// 正規表現でIP→MACの対応を抽出
// 例: "192.168.1.10 dev wlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE"
```

### WolSender.kt
```kotlin
// Magic Packet 生成: ByteArray(6){0xFF.toByte()} + mac*16
// DatagramSocket().send(DatagramPacket(packet, packet.size, broadcast, port))
```

### PingSender.kt
```kotlin
// val start = System.currentTimeMillis()
// val reachable = InetAddress.getByName(ip).isReachable(2000)
// val elapsed = System.currentTimeMillis() - start
// return PingResult(reachable, elapsed)
```

---

## build.gradle (app) の依存関係

```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-android-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
```

---

## 実装上の注意事項

1. **UDPはコネクションレス** — WoL送信の成否はパケット到達で判断不可。例外なく完了したら「送信完了」と表示する。
2. **ARPテーブルは保証なし** — 取得できないケースを必ずハンドリングし、MACを空のままデバイス登録できるようにする。
3. **LANスキャンはUIスレッドをブロックしない** — 進捗はFlowで流してUI側で購読する。
4. **サブネット検出** — `WifiManager.connectionInfo.ipAddress` はdeprecatedだがAPI 26サポートのため使用可。API 31以上では `LinkProperties` を使う分岐を入れること。
5. **スキャン中断** — 画面離脱時にCoroutineをキャンセルできるよう `viewModelScope` で管理する。

---

## 実装順序

1. プロジェクト作成・Gradle設定・Hilt初期化
2. `Device` モデル・Room DB・DAO
3. `WolSender` + `WolUseCase`
4. `PingSender` + `PingUseCase`
5. `IpScanner` + `ArpResolver` + `LanScanUseCase`
6. UI: `DeviceListScreen` → `DeviceDetailScreen` → `ScanScreen`
7. Navigation 接続・全体動作確認

---

## 進捗管理

詳細ログは `D:\iwa\AI\Claude\private_notes\PROGRESS.md` を参照すること。
