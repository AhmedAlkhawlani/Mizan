# 02 — منصة أندرويد: ما يمكن وما لا يمكن

> هذا أهم ملف في المخطط. أي قرار مخالف لما هنا سيُنتج تطبيقاً لا يعمل على الأجهزة الحقيقية.

## 1) قياس السرعة اللحظية — ممكن بدون صلاحيات

| API | ماذا يعطي | ملاحظات |
|---|---|---|
| `TrafficStats.getTotalRxBytes()/getTotalTxBytes()` | إجمالي الجهاز التراكمي منذ الإقلاع | لا يحتاج صلاحية؛ يشمل Wi‑Fi وخلوي |
| `TrafficStats.getMobileRxBytes()/getMobileTxBytes()` | الخلوي فقط | تراكمي |
| `TrafficStats.getRxBytes(iface)` | واجهة محددة (wlan0/rmnet0) | مفيد لفصل Wi‑Fi عن الخلوي |
| `TrafficStats.getUidRxBytes(uid)` | **غير موثوق** لأي UID غير تطبيقك | منذ Android 7 يعيد `UNSUPPORTED` |

**القرار:** السرعة اللحظية تُحسب من فرق العدّادات الكلية على مستوى الجهاز/الواجهة كل ثانية.
لا نحاول نسبة السرعة اللحظية لتطبيق معيّن إلا في مرحلة `VpnService`.

## 2) استهلاك كل تطبيق — ممكن مع Usage Access

| الخيار | يحتاج | يعطي | حدود |
|---|---|---|---|
| `NetworkStatsManager.queryDetailsForUid(...)` | صلاحية `PACKAGE_USAGE_STATS` عبر إعدادات النظام | بايتات لكل UID/Hash داخل نطاق زمني | بكت (bucket) وليس لحظياً؛ قد يتأخر دقائق |
| `NetworkStatsManager.querySummaryForDevice(...)` | نفس الصلاحية | ملخص الجهاز حسب نوع الشبكة | للتحقق والمقارنة |
| `UsageStatsManager` | نفس الصلاحية | التطبيق في المقدمة | اختياري لتصنيف «خلفية/أمامية» |
| `VpnService` + TUN | موافقة VPN | بايتات لحظية لكل تطبيق + إمكانية الحظر | VPN نشط واحد فقط؛ أيقونة VPN؛ استهلاك بطارية |

**القرار:**
- v1: `NetworkStatsManager` للاستهلاك التاريخي لكل تطبيق، مع مزامنة كل 15 دقيقة وعند فتح التطبيق.
- مرحلة 6: `VpnService` للقياس اللحظي والحظر الحقيقي.

**كيف تُطلب الصلاحية؟**
```kotlin
val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
```
والتحقق:
```kotlin
val appOps = context.getSystemService(AppOpsManager::class.java)
val mode = appOps.checkOpNoThrow(
    AppOpsManager.OPSTR_GET_USAGE_STATS,
    Process.myUid(),
    context.packageName
)
val granted = mode == AppOpsManager.MODE_ALLOWED
```
وتُعلَن في المانيفست:
```xml
<uses-permission
    android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
```

## 3) الشبكات المقنّنة والهوتسبوت

| السؤال | API | ملاحظة |
|---|---|---|
| هل الشبكة الحالية مقنّنة؟ | `ConnectivityManager.isActiveNetworkMetered` | ✅ الأساس |
| هل المستخدم قال «غير مقنّنة» مؤقتاً؟ | `NET_CAPABILITY_TEMPORARILY_NOT_METERED` (API 30) | ✅ |
| Wi‑Fi أم خلوي؟ | `NetworkCapabilities.TRANSPORT_WIFI/CELLULAR` | ✅ |
| تجوال؟ | `NET_CAPABILITY_NOT_ROAMING` | ✅ |
| اسم الشبكة (SSID)؟ | `WifiInfo` من `NetworkCapabilities` | ⚠️ يحتاج `ACCESS_FINE_LOCATION` على Android 10+ |
| هل أنا نقطة اتصال (Hotspot)؟ | `TetheringManager`/حالة النظام | ⚠️ واجهات محدودة |
| استهلاك الدورة من المشغّل | `NetworkStatsManager` + `SubscriptionManager` | ⚠️ لا توجد واجهة موحّدة موثوقة |

**القرار:** لا نطلب صلاحية الموقع لأجل SSID. بدلاً منها:
- نستخدم `isActiveNetworkMetered` كمصدر أساسي.
- المستخدم ينشئ **ملفات تعريف** (اسم حر): «البيت — غير مقنّن»، «هوتسبوت — مقنّن 2GB»، ويربطها يدوياً بالشبكة الحالية.
- عند تغيّر الشبكة نسأل مرة واحدة: «هل نعتبر هذه الشبكة مقنّنة؟».

## 4) التنبيهات التي «لا تختفي إلا بالضغط»

| القدرة | API | الحقيقة |
|---|---|---|
| إشعار دائم لا يُكنسل بالسحب | `setOngoing(true)` + `setAutoCancel(false)` | ✅ على معظم الأجهزة، مع اختلافات من الشركات |
| صوت عند الظهور | قناة `IMPORTANCE_HIGH` + `setSound` | ✅ |
| إبقاء الشاشة/إظهار ملء الشاشة | `USE_FULL_SCREEN_INTENT` | ❌ مقيّد في Android 14 لتطبيقات المكالمات/المنبّه فقط |
| إشعار يتطلب ضغطاً | `setOngoing(true)` + إجراء «حسناً» | ✅ الأفضل عملياً |
| إعادة إظهاره دورياً | `AlarmManager`/`WorkManager` | ⚠️ Exact alarms مقيّدة على 12+ |

**القرار:** قناة `IMPORTANCE_HIGH` + إشعار `ongoing` بصوت وإجراء «حسناً»، مع إعادة الإظهار عند
كل حدث. لا نعتمد على full-screen intent.

## 5) الخدمة الأمامية (Foreground Service)

- مطلوبة لقياس مستمر؛ بدونها سيقتل النظام العملية.
- Android 14+: يجب تحديد `foregroundServiceType`:
  - `dataSync` ⇒ يتطلب `FOREGROUND_SERVICE_DATA_SYNC`، ومقيّد في الخلفية.
  - `specialUse` ⇒ يتطلب `FOREGROUND_SERVICE_SPECIAL_USE` وتبريراً في Play.
- إشعار الخدمة إلزامي ولا يمكن إخفاؤه.
- **القرار:** `specialUse` مع تبرير واضح، مع مفتاح «إيقاف المراقبة» للمستخدم، وتقليل التحديث للثانية فقط أثناء التشغيل الظاهر.

## 6) العمل في الخلفية

| الأداة | الاستخدام | القيود |
|---|---|---|
| `ForegroundService` | القياس اللحظي | إشعار إلزامي |
| `WorkManager` | مزامنة كل 15 دقيقة | الحد الأدنى 15 دقيقة |
| `AlarmManager` | منتصف الليل/الدورة | Exact يحتاج صلاحية ومقيّد |
| `RECEIVE_BOOT_COMPLETED` | إعادة الجدولة بعد الإقلاع | ✅ |

**القرار:** التصفير اليومي يُفحص عند: فتح التطبيق، بدء الخدمة، واستيقاظ WorkManager. لا نعتمد على منبّه دقيق.

## 7) `VpnService` للحظر الحقيقي (مرحلة 6)

- `VpnService.prepare()` → موافقة المستخدم.
- `Builder.addAllowedApplication` / `addDisallowedApplication` لكل تطبيق.
- TUN يعيد توجيه الحزم؛ نستطيع عدّها لكل تطبيق وإسقاط حزم المحظور.
- قيود: VPN واحد فقط؛ لا يعمل مع VPN آخر؛ أيقونة في شريط الحالة؛ استهلاك بطارية؛ سياسات Play صارمة.
- **القرار:** مرحلة منفصلة اختيارية، مع شاشة شرح صريحة، وبدون أي إرسال بيانات خارج الجهاز.

## 8) الصلاحيات المطلوبة (القائمة النهائية لـ v1)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
    tools:ignore="QueryAllPackagesPermission" />
```
- `QUERY_ALL_PACKAGES` لأجل عرض أسماء التطبيقات؛ بديله `<queries>` إن أمكن لتقليل سياسة Play.
- لا `READ_PHONE_STATE`، ولا `ACCESS_FINE_LOCATION` في v1.

## 9) خصوصية ودقة — قواعد صارمة

1. لا نجمع أسماء مواقع ولا DNS ولا محتوى.
2. لا نرسل أي شيء للخارج؛ التطبيق يعمل كلياً offline.
3. عندما نعرض رقماً من `NetworkStatsManager` نقول إنه «حتى آخر مزامنة»، لا «لحظي».
4. عند تعذّر القياس نعرض «—» ولا نخمّن.
5. تُوسم الأيام الجزئية `partial` في قاعدة البيانات والواجهة.

## 10) مصفوفة القدرات باختصار

| الميزة | ممكن؟ | الطريقة | ملاحظة |
|---|---|---|---|
| سرعة لحظية للجهاز | ✅ | TrafficStats | دقة جيدة |
| سرعة لحظية لكل تطبيق | ⚠️ | VpnService فقط | مرحلة 6 |
| استهلاك كل تطبيق | ✅ | NetworkStatsManager | يحتاج Usage Access |
| منع تطبيق من الشبكة | ✅ | VpnService | بدون root |
| قتل تطبيق | ✅ | `ActivityManager.killBackgroundProcesses` | غير موثوق؛ نتجنبه — الحظر أفضل |
| تمييز مقنّن | ✅ | isActiveNetworkMetered | ✅ |
| SSID الشبكة | ⚠️ | WifiInfo + موقع | نتجنبه في v1 |
| إشعار دائم بصوت | ✅ | Ongoing + channel | اختلافات OEM |
| Widget | ✅ | Glance | ✅ |
| QS Tile | ✅ | TileService | ✅ |
