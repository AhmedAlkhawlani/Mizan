# 15 — سياسة الخصوصية

> **سارية من:** 2026-11-05 · **تنطبق على:** Mizan 1.0.0 (`com.maknoon.mizan`)

## بالعربية

### الخلاصة

ميزان عدّاد بيانات يعمل على جهازك وحده. **لا يجمع شيئاً، ولا يرسل شيئاً، ولا يملك صلاحية الاتصال
بالإنترنت أساساً.** كل الأرقام التي يراها المستخدم تُقرأ من نظام أندرويد وتُخزَّن في قاعدة بيانات
محلية خاصة بالتطبيق.

### ما لا نفعله إطلاقاً

- لا حساب، ولا تسجيل، ولا بريد إلكتروني.
- لا تحليلات، ولا تتبّع، ولا إعلانات، ولا معرّفات إعلانية.
- لا اتصال بأي خادم: التطبيق **لا يعلن** صلاحية `android.permission.INTERNET`، فلا يستطيع
  إرسال بايت واحد إلى الشبكة، ولا استقبال تحديثات أو تعليمات من أي طرف.
- لا قراءة للرسائل، ولا لجهات الاتصال، ولا للموقع، ولا لسجل المكالمات، ولا لمعرّفات الجهاز
  (IMEI/MAC/SSID/Android ID).
- لا بيع أو مشاركة بيانات مع أي جهة، لعدم وجود بيانات تُشارك.

### ما يقرأه التطبيق، وأين يبقى

| المصدر | ما يُقرأ | أين يُخزَّن | لماذا |
|---|---|---|---|
| `TrafficStats` | عدّادات بايتات الجهاز (رفع/تنزيل) لكل واجهة | Room داخل التطبيق، بالساعة وباليوم | السرعة اللحظية والسجل والميزانيات |
| `NetworkStatsManager` | عدّادات بايتات كل تطبيق، بعد منح صلاحية الاستخدام | Room داخل التطبيق | تبويب التطبيقات وتفاصيلها |
| `ConnectivityManager` | نوع الشبكة الحالية (خلوي/Wi‑Fi/غير مقنّن) | حالة لحظية، تُحفظ راية المقنّن في DataStore | الانقسام والاستثناء |
| إعدادات المستخدم | الميزانيات، يوم الدورة، التطبيقات المحظورة | DataStore وRoom داخل التطبيق | تذكّر اختياراتك |

كل ما سبق يبقى على الجهاز. لا نسخة سحابية: `android:allowBackup="false"` يمنع حتى النسخ
الاحتياطي التلقائي لنظام أندرويد من أخذ قاعدة البيانات.

### جدار الحماية (اختياري)

عند تفعيله، ينشئ التطبيق نفق `VpnService` **محلياً وبغرض الحجب فقط**:

- لا يوجّه الترافيك إلى خادم، ولا يمرّ عبر أي طرف ثالث.
- لا يفكّ تشفيراً ولا يقرأ محتوى الحزم؛ يقرأ ترويسة IP فقط ليعرف أي تطبيق أرسل الحزمة،
  ويُسقط حزم التطبيقات التي اخترت حجبها، ويعدّ المحاولات المحجوبة.
- التطبيقات غير المحظورة تُستثنى من النفق ولا تمرّ به أصلاً.
- إيقاف المفتاح أو إلغاء الموافقة من إعدادات النظام يُغلق النفق فوراً.

### التصدير والمشاركة

تصدير CSV يحدث فقط عندما تطلبه أنت، عبر منتقي ملفات النظام، ويُكتب الملف حيث تختار. لا يقرأ
ميزان أي ملف ولا يرفعه. مسؤولية ما تفعله بالملف بعد تصديره عليك.

### الاحتفاظ والحذف

- السجل اليومي وبالساعة يبقى في قاعدة بيانات التطبيق حتى تحذفه.
- **مسح بيانات التطبيق** من إعدادات أندرويد يمسح كل السجل والإعدادات نهائياً.
- إزالة التطبيق تمسح بياناته أيضاً.
- لا نملك نسخة من بياناتك على أي خادم، فلا يمكننا استرجاعها أو حذفها نيابة عنك — لا شيء لدينا.

### الأطفال

التطبيق لا يجمع بيانات من أي مستخدم، بمن فيهم الأطفال، ولا يعرض إعلانات.

### التغييرات

أي تغيير جوهري في هذه السياسة يُوثَّق في المستودع مع الإصدار الذي أدخله. الإصدار الحالي لا
يملك أي مسار لإرسال بيانات، فإن أُضيف مستقبلاً سيُعلن صراحةً هنا وفي ملاحظات الإصدار.

### التواصل

للاستفسار أو الإبلاغ عن مشكلة خصوصية، افتح Issue في مستودع المشروع، أو راسل المشرف عبر قناة
المشروع نفسها. لا نطلب بيانات شخصية للرد.

---

## In English

### Summary

Mizan is a data meter that runs entirely on your device. It collects nothing, sends nothing, and
does not even hold the `android.permission.INTERNET` permission, so it is technically incapable
of network communication. Every figure it shows is read from Android and stored in the app's
private local database.

### What Mizan never does

- No account, sign-up, or email address.
- No analytics, tracking, ads, or advertising identifiers.
- No server communication. Without the `INTERNET` permission the app cannot send or receive a
  single byte and cannot be updated or instructed remotely.
- No access to SMS, contacts, location, call logs, or device identifiers (IMEI/MAC/SSID/Android ID).
- No selling or sharing of data, because there is no data to share.

### What it reads and where it stays

| Source | Read | Stored | Purpose |
|---|---|---|---|
| `TrafficStats` | Device byte counters per interface | Room, hourly and daily, inside the app | Live speed, history, budgets |
| `NetworkStatsManager` | Per-app byte counters, after you grant Usage access | Room, inside the app | Apps tab and app details |
| `ConnectivityManager` | Current network type (mobile/Wi‑Fi/unmetered) | Live state; a metered flag in DataStore | Split and unmetered exception |
| Your settings | Budgets, cycle day, blocked apps | DataStore and Room, inside the app | Remembering your choices |

Nothing leaves the device. Cloud backup is disabled (`android:allowBackup="false"`), so even
Android's automatic backup cannot copy the database off the phone.

### Firewall (optional)

When enabled, the app creates a **local, blocking-only** `VpnService` tunnel:

- Traffic is never routed to a server or any third party.
- No decryption and no payload inspection. The app reads IP headers only to attribute a packet to
  an app, drops packets from apps you blocked, and counts blocked attempts.
- Apps you did not block are excluded from the tunnel entirely.
- Turning the switch off, or revoking VPN consent in system settings, tears the tunnel down at once.

### Export and sharing

A CSV export happens only when you request it, through the system file picker, and is written
wherever you choose. Mizan never reads or uploads files. What you do with the exported file is up
to you.

### Retention and deletion

- Usage history stays in the app's database until you delete it.
- **Clear app data** in Android settings erases all history and preferences permanently.
- Uninstalling the app also erases its data.
- We hold no copy of your data on any server, so there is nothing for us to retrieve or delete.

### Children

The app collects no data from any user, including children, and shows no ads.

### Changes

Any material change to this policy is recorded in the project repository alongside the release
that introduces it. The current version has no data-egress path at all; if one is ever added, it
will be stated explicitly here and in the release notes.

### Contact

For privacy questions, open an Issue in the project repository or reach the maintainer through the
project's own channel. No personal information is required to get an answer.
