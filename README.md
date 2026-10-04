# wol-light

[English](#english) | [日本語](#日本語)

## English

WolLight is a Wake-on-LAN app for Android. It sends Wake-on-LAN magic packets to
PCs on the same local network and keeps a small list of the devices you wake
regularly. It was built to replace the Wake-on-LAN feature of Fing.

- Platform: Android 8.0 (API 26) or later, Wi-Fi connection required
- UI languages: English (default) and Japanese (used when the device language
  is Japanese)
- Works on the local network only; no account and no cloud service

### Features

- **LAN scan**: selects a non-VPN Wi-Fi or Ethernet IPv4 subnet and uses its
  actual CIDR prefix. Network, broadcast and the device's own address are excluded.
  Scans up to 1,024 peers with bounded concurrency and progress based on the target
  count; larger or ambiguous networks are rejected instead of silently narrowed.
  Host names are looked up over NetBIOS and mDNS; the IP address is shown when no
  name is found. A VPN may still restrict LAN reachability.
- **Device registration**: register a device from the scan results with a name
  of your choice. Name, MAC address, broadcast address and port can be edited,
  and devices can be deleted. The list is kept on the device across restarts.
- **Wake-on-LAN**: sends a standard 102-byte magic packet as a UDP broadcast
  (default `255.255.255.255`, port `9`; the port is configurable).
- **Ping**: sends ten pings at one-second intervals, shows each result with its
  response time, then a summary (successes and average time).
- **Online / offline indicator**: shown per device in the list, based on ping
  results on app start, on return to the foreground and on manual ping. The
  state changes only after two consecutive matching results, to avoid false
  readings from transient packet loss.
- **Stale-IP recovery**: if a registered device cannot be reached at its saved
  IP address, WolLight runs one LAN scan and looks for the same MAC address. If
  the device is found at a different address, the saved IP address is updated
  only after you confirm. A registered MAC address is required.

### Limitations

- Local network only. Wake-on-LAN uses broadcast packets, so only devices on the
  same LAN can be woken. Waking a PC over the internet needs a separate VPN or
  port-forwarding setup that WolLight does not provide.
- Whether the PC wakes depends on the PC: Wake-on-LAN must be enabled in
  BIOS/UEFI and the network adapter, and on Windows disabling Fast Startup is
  recommended. In the test environment, waking from hibernation worked; waking
  from full shutdown did not.
- UDP is connectionless, so delivery of a magic packet cannot be confirmed. Use
  ping to check whether the PC has come up.
- Automatic host-name lookup often fails on home networks (NetBIOS is disabled
  on Windows 10/11, mDNS needs Bonjour). Give each device a name when you
  register it.
- Tested only on a Pixel 10a so far.

### Privacy and network behaviour

- All traffic stays on the local network: magic packets, pings and host-name
  resolution (NetBIOS, mDNS, ARP).
- No analytics, telemetry or crash reporting.
- No external HTTP services and no cloud sync.
- Registered device details (name, IP address, MAC address, broadcast address,
  port) are stored only in a local database (Room) on the device.

### Install

1. Open the [v1.0.5 Release](https://github.com/iwadjp/wol-light/releases/tag/v1.0.5) page.
2. Download `wol-light-v1.0.5-android.apk` from Assets.
3. Open the APK to install it (sideload). WolLight is not distributed on Google
   Play.

Android may ask you to allow installs from this source, and Google Play Protect
may warn about APKs from outside Google Play. Make sure the APK comes from the
official GitHub Release above.

### Build

```powershell
.\gradlew.bat assembleDebug      # debug APK
.\gradlew.bat testDebugUnitTest  # JVM unit tests
.\gradlew.bat lintDebug          # Android lint
```

On macOS/Linux, use `./gradlew` instead of `.\gradlew.bat`.

### License

[MIT License](LICENSE)

---

## 日本語

Fing の Wake on LAN 機能を代替する Android アプリ。

---

## 機能

### LAN スキャン
- VPNを除くWi-FiまたはEthernetのIPv4サブネットを選び、実際のCIDR prefixを使用
- network / broadcast / 自端末アドレスを除外し、最大1,024対象を並列数制限付きでスキャン
- 進捗は実際の対象数を使用。巨大または判断不能なサブネットは縮小せずエラー表示
- VPNによってLANへの到達性が制限される場合がある
- 生存確認済みのIPアドレスを一覧表示
- ホスト名取得を試みる（NetBIOS / mDNS）。取得できない場合はIPアドレスを表示

### デバイス管理
- スキャン結果からデバイスを選択して登録
- 登録時に任意の名前を付けられる
- 登録済みデバイスはアプリ再起動後も保持
- デバイス情報の編集（名前・MACアドレス・ブロードキャストアドレス・ポート）
- デバイスの削除

### on/off 表示
- 登録済みデバイス一覧にオンライン状態をインジケーターで表示
  - 🟢 緑: オンライン
  - ⚫ 灰: オフライン／不明

**更新タイミング**
- アプリ起動時（全デバイスに自動Ping）
- アプリ復帰時（バックグラウンドからフォアグラウンドへ）
- 手動Ping送信時

**更新ロジック**
- 現在の状態と異なる結果が**連続2回**続いた場合のみ状態を更新
- 一時的なパケットロス等による誤検知を防止
- アプリ再起動でリセット（DBには保存しない）

### WoL 送信（Wake on LAN）
- Magic Packet（102バイト）を UDP ブロードキャストで送信
- 宛先: `255.255.255.255`、ポート: `9`（変更可能）
- 送信結果を Snackbar で通知

### Ping 送信
- 1秒間隔で10回 Ping を送信
- 結果をリアルタイムに表示（✅ 成功 / ❌ 失敗・応答時間ms）
- 10回完了後に集計を表示（成功回数・平均応答時間）
- 連続2回同じ結果でon/off状態が変わった時点で即時反映

---

## 画面構成

```
DeviceListScreen（起動画面）
  ├── [スキャンボタン] → ScanScreen
  │     └── [デバイス登録] → DeviceListScreen（更新）
  └── [デバイスタップ] → DeviceDetailScreen
        ├── [WoL送信ボタン]
        ├── [Ping送信ボタン]
        └── [編集・削除]
```

---

## 動作要件

| 項目 | 要件 |
|---|---|
| OS | Android 8.0 以上（API 26+） |
| ネットワーク | WiFi 接続必須 |
| WoL対象PC | BIOS/UEFI で Wake on LAN が有効であること |
| WoL対象PC | Windows の高速スタートアップを無効にすること推奨 |
| WoL対象PC | NIC の電源管理で「Magic Packet でのみスタンバイ解除」を有効にすること推奨 |

---

## 対象PCのWindows設定

### 高速スタートアップの無効化
```
コントロールパネル → 電源オプション
→ 電源ボタンの動作を選択する
→ 高速スタートアップを有効にする → オフ
```

### NIC 電源管理の設定（Pingでの誤起動防止）
```
デバイスマネージャー → ネットワークアダプター
→ 対象NICを右クリック → プロパティ
→ 電源の管理タブ
→「Magic Packet でのみ、コンピューターのスタンバイ状態を解除できるようにする」にチェック
```

---

## 技術スタック

| 項目 | 内容 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose |
| 非同期 | Coroutines + Flow |
| DI | Hilt |
| DB | Room |
| 最小SDK | API 26 (Android 8.0) |
| ターゲットSDK | API 36 (Android 16) |

---

## パーミッション

| パーミッション | 用途 |
|---|---|
| INTERNET | LAN通信・WoL送信・Ping |
| ACCESS_WIFI_STATE | サブネット検出 |
| CHANGE_WIFI_MULTICAST_STATE | mDNS（ホスト名取得） |
| ACCESS_NETWORK_STATE | ネットワーク状態確認 |

---

## 既知の制限事項

### ホスト名の自動取得
家庭内LANではホスト名の自動取得が困難なケースがある。

| 手段 | 結果 |
|---|---|
| 逆引きDNS | 家庭内LANでは機能しない |
| NetBIOS NBNS | Windows 10/11 で無効・廃止済みのため機能しない |
| mDNS PTR | Bonjour 未インストール環境では機能しない |

→ 登録時にユーザーが任意の名前を設定することで対応。

### WoL の到達確認
UDP はコネクションレスのため、Magic Packet が対象PCに届いたかどうかの確認はできない。Ping で起動を確認すること。

### 同一LAN内のみ有効
WoL はブロードキャストパケットのため、同一LAN内のデバイスのみが対象。外部ネットワークから起動する場合は VPN またはポートフォワードの設定が別途必要。

### IP アドレス復旧（v1.0.1〜）
IP アドレス復旧には登録済み MAC アドレスが必要。復旧はデバイス詳細画面での Ping 試行が失敗した場合にのみ実行され、登録アドレスの更新には明示的な確認操作が必要。

### WoL の成否は対象PC側の設定に依存する
WolLight は標準的な WoL Magic Packet を送信するだけであり、対象PCが実際に起動できるかどうかは、対象PC側の BIOS/UEFI 設定・NIC の WoL 対応・Windows の電源管理設定（特に高速スタートアップ）に依存する。WolLight 側の不具合ではなく、環境依存の制約として扱う。

動作確認環境では、休止状態からの起動は成功しているが、シャットダウン状態からの起動は確認環境では成功していない。

---

## 将来の改善候補

- MACアドレスベンダー情報の表示（OUIリスト同梱）
- ルーターの DHCP テーブル参照によるホスト名取得
- kapt → KSP への移行（AGP 10.0 対応）
- LocalLifecycleOwner の deprecated 警告対応

---

## インストール

1. [v1.0.5 Release](https://github.com/iwadjp/wol-light/releases/tag/v1.0.5) ページを開く。
2. Assets から `wol-light-v1.0.5-android.apk` をダウンロードする。
3. ダウンロードした APK をタップしてインストールする（サイドロード）。Google Play での配布は行っていない。

Android では、このソースからのアプリインストールを許可するよう求められる場合がある。また Google Play Protect や Android が、Google Play 外で配布された APK に対して警告を表示することがある。インストールを進める前に、ダウンロードした APK が上記の公式 GitHub Release から取得したものであることを確認すること。

現在は Pixel 10a でのみ動作確認済み。他の端末での動作は未検証。

---

## ビルドと検証

```powershell
# Debug APKを作成
.\gradlew.bat assembleDebug

# JVM unit testを実行
.\gradlew.bat testDebugUnitTest

# Android lintを実行
.\gradlew.bat lintDebug
```

macOS/Linux では `.\gradlew.bat` を `./gradlew` に読み替える。

---

## プライバシー / ネットワーク動作

WolLight が行う通信は同一LAN内のみで、外部サーバーへのデータ送信は行わない。

- WoL Magic Packet の送信、Ping（到達確認）、ホスト名解決（NetBIOS / mDNS / ARP）はいずれも同一LAN内の通信のみ
- analytics・telemetry・クラッシュレポート機能は実装していない
- 外部HTTPサービスへの通信、クラウド同期は行わない
- 登録したデバイス情報（名前・IPアドレス・MACアドレス・ブロードキャストアドレス・ポート）は端末内のローカルDB（Room）にのみ保存される

---

## ライセンス

[MIT License](LICENSE)
