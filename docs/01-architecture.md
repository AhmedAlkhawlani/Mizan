# 01 — الهندسة والوحدات

## نظرة عامة

```text
┌────────────────────────────────────────────────────────────┐
│                        :app (UI + DI)                       │
│  Compose · Navigation · ViewModels · Glance · TileService   │
└───────────────▲──────────────────────────────▲─────────────┘
                │ Flow/StateFlow               │
┌───────────────┴──────────────────────────────┴─────────────┐
│                     :core:domain                            │
│  UseCases · Models · ByteFormatter · Accountant · Policies  │
└───────────────▲──────────────────────────────▲─────────────┘
                │                              │
┌───────────────┴───────────┐   ┌──────────────┴─────────────┐
│        :core:data         │   │      :core:platform        │
│ Room · DataStore · Repos  │   │ TrafficStats · NetworkStats│
│ WorkManager · Alerts      │   │ Connectivity · VpnService  │
└───────────────────────────┘   └────────────────────────────┘
```

## الوحدات (modules)

| الوحدة | المسؤولية | يعتمد على |
|---|---|---|
| `:app` | Compose UI، التنقل، الـ ViewModels، DI، Widgets، Tile | domain, data, platform, designsystem |
| `:core:domain` | منطق خالص بلا أندرويد: الحسابات، الفورمتر، السياسات | لا شيء (Kotlin/JVM فقط) |
| `:core:data` | Room، DataStore، المستودعات، WorkManager، الإشعارات | domain, platform |
| `:core:platform` | أغلفة حول APIs أندرويد: TrafficStats، NetworkStats، Connectivity، VpnService | domain |
| `:core:designsystem` | الألوان، الخطوط، المكوّنات المشتركة، RTL | Compose فقط |
| `:feature:dashboard` | شاشة السرعة والملخص | app-level |
| `:feature:apps` | قائمة التطبيقات والسجل والميزانيات | app-level |
| `:feature:saver` | ملفات الشبكات والحظر | app-level |
| `:feature:settings` | الإعدادات واللغة | app-level |

> **قاعدة عملية:** ابدأ بـ `:app` + `:core:domain` + `:core:data` + `:core:platform`
> في المرحلة 0، ثم فكّك `:feature:*` عندما يكبر الكود. لا تبدأ بعشر وحدات فارغة.

## طبقات المنطق الخالص (domain)

كل ما يخص الحساب يجب أن يعمل على JVM بلا محاكي أندرويد، ويُختبر بـ JUnit:

- `ByteFormatter` — وحدات عشرية (1 KB = 1000 B) مثل مزوّدي الخدمة.
- `TrafficAccountant` — تصفير يومي، إعادة تشغيل، فجوات، partial.
- `BudgetPolicy` — عتبات 80/100 مرة واحدة لكل دورة.
- `AnomalyDetector` — انحراف عن الوسيط (median) لا عن المتوسط.
- `CycleCalculator` — دورة الفاتورة (يوم البداية) وحساب الشهر.
- `AppUsageAggregator` — تجميع UID → تطبيق، دمج العمليات المساعدة.
- `NetworkProfileResolver` — أي ملف تعريف ينطبق على الشبكة الحالية.

## تدفّق البيانات

1. **لحظي (1s)**: `ForegroundService` → `TrafficStats` → `TrafficAccountant` → `StateFlow` → UI/Widget/Tile/Notification.
2. **دوري (15min)**: `WorkManager` → `NetworkStatsManager.queryDetailsForUid` → فروقات → Room.
3. **يومي**: عند تغيّر اليوم (`CycleCalculator`) → أرشفة اليوم في Room + تصفير + تقييم الميزانية.
4. **أحداث**: تغيّر الشبكة (`ConnectivityManager.NetworkCallback`) → تحديث ملف التعريف والمقنّن.

## الخيوط والـ Coroutines

- `Dispatchers.Default` للمنطق الحسابي.
- `Dispatchers.IO` لقاعدة البيانات وAPIs النظام.
- الخدمة الأمامية تعمل على `CoroutineScope` مرتبط بدورة حياتها.
- الـ UI يجمع `StateFlow` بـ `collectAsStateWithLifecycle`.
- لا عمل ثقيل على `Dispatchers.Main` مطلقاً.

## الحقن (DI)

- **Hilt** مع KSP.
- `@Module` لكل طبقة: `DomainModule`, `DataModule`, `PlatformModule`.
- في الاختبارات: `@TestInstallIn` لاستبدال المستودعات بـ Fakes.

## هيكل الحزم

```text
com.maknoon.mizan
├── app/            MainActivity, MizanApplication, navigation, di
├── core/
│   ├── domain/     model, accounting, budget, format, cycle
│   ├── data/       db, datastore, repository, worker, notification
│   ├── platform/   trafficstats, networkstats, connectivity, vpn
│   └── designsystem/ theme, components, icons
├── feature/
│   ├── dashboard/
│   ├── apps/
│   ├── saver/
│   └── settings/
└── widget/         glance, tile
```

## قواعد الكود

1. لا يلمس `domain` أي `import android.*`.
2. المستودعات تُعرَّف كواجهات في `domain` وتُنفَّذ في `data`.
3. كل زمن يُمرَّر كـ `Clock` أو `TimeProvider` — لا `System.currentTimeMillis()` مباشرة في المنطق.
4. كل بايت يُخزَّن `Long` (bytes)، والعرض يتحول في طبقة العرض فقط.
5. كل شاشة لها `UiState` واحد غير قابل للتعديل + أحداث.
6. الأخطاء تُغلَّف في `MizanResult<T>` بدل رمي استثناءات في الواجهة.
