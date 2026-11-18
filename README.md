# Cute Reminder & SOS

Routines + alarms, an SOS that takes over your emergency contacts' phones, and a private
"Personal" circle (chat, reminders, alarms, live location, listen-together music).

## Kya-kya hai

| Feature | Kaise kaam karta hai |
|---|---|
| **Login** | Phone **OTP** (Firebase Auth) ya **Google Sign-In**. Google se aane par bhi mobile number OTP se verify hota hai, taaki number kisi aur ka na ban sake. |
| **Emergency contacts** (SOS Hub) | Kisi ka bhi naam + number daalo, acceptance nahi chahiye. SOS sirf unhi ko jata hai jinhe aapne add kiya. Jinke paas app + login hai: ring + live location + aapka number. Baaki sabko SMS + map link. |
| **Mutual** (alag tab) | Number daalo, wo accept kare. Phir chat, music, alarm, daily routine, pending tasks dono ko dikhte hain. Chat delete, aur kisi ko block karne ka option. Emergency list se bilkul alag. |
| **SOS takeover** | Emergency contact ke phone par: screen on, lock-screen ke upar full-screen, **alarm full volume**, back button band, screen-pinning (best effort). "I see it" dabane par siren band + sender ko "dekh liya" ka notification. |
| **Offline SOS backup** | Settings me ek backup number: SOS par SMS + map link (bina internet / bina app ke). |
| **Live location** | SOS Hub me switch. Sirf unko dikhti hai jinke saath share ki hai. "Location ON karo" request bhi bhej sakte ho. |
| **Reminder → person** | Routine banate waqt "Ye reminder kisko bhi bhejna hai?" me person chuno. Alarm bajte hi unko bhi notification jata hai. |
| **Alarm for each other** | Person → Remind → time chuno → unke phone par alarm set ho jata hai. |
| **Chat** | Person → Chat. Realtime, notification ke saath. Message par long-press: copy / delete for everyone. ⋮ menu: chat save (share as text), chat saaf (sirf mere phone se), meri saari messages delete (sabke liye). |
| **Profile** | Pehli baar login par photo, naam, email, blood group, medical note, address. Settings → Edit se badal sakte ho. Contacts "Phone contacts se chuno" se add hote hain (sirf wahi contact padha jata hai jo aap tap karo). |
| **Music together** | Person → Music → direct audio link (mp3/stream). Play/pause/seek dono phones par sync. |
| **Call** | Person screen par call button (phone dialer). |

## Women's safety toolkit (SOS Hub)

| Tool | Kya karta hai |
|---|---|
| **Safe Walk timer** | 15 min / 30 min / 1 hr / 2 hr chuno. "I'm safe" nahi dabaya to timer khatam hote hi SOS + live location khud chali jati hai (app band ho tab bhi). 1 min pehle warning notification aata hai. |
| **Shake-to-SOS** | Phone ko 4 baar zor se hilao → SOS (screen band ho tab bhi). Switch se ON/OFF. |
| **Fake call** | "Mom" (ya koi naam) ka asli jaisa incoming call, abhi / 10s / 30s / 1 min baad. Lock-screen par bhi. |
| **Loud siren** | Sirf awaaz, kisi ko alert nahi. Alarm stream par bajta hai, **silent mode me bhi**. |
| **Helplines** | 112, 1091 (Women Helpline), 181, 100 — ek tap me call. |
| **Medical info with SOS** | Profile me blood group / medical note / address bharo; ye SOS ke saath emergency contacts ko dikhta hai. |
| **Reminder alarms** | Har reminder 30 sec tak real alarm ki tarah bajta hai (silent mode me bhi), "Stop alarm" button ke saath. |

## Firebase se kya chahiye (checklist)

| # | Cheez | Kahan | Zaruri? |
|---|---|---|---|
| 1 | Project + Android app (package name, SHA-1, SHA-256) | Console → Project settings | Haan |
| 2 | `google-services.json` → `app/` | Project settings → Your apps | Haan |
| 3 | Authentication → **Phone** ON (+ test numbers) | Authentication → Sign-in method | Haan |
| 4 | Authentication → **Google** ON, phir json dobara download | same | Haan |
| 5 | **Firestore Database** + `firebase deploy --only firestore:rules` | Firestore | Haan |
| 6 | **Cloud Messaging** (FCM) | Auto ON hota hai | Haan |
| 7 | **Functions** deploy (`pushEvent`) — Blaze plan | `firebase deploy --only functions` | Push ke liye |
| 8 | Cloud Storage | — | **Nahi**. Profile photo chhoti karke Firestore me rakhi jati hai (Storage ab Blaze maangta hai). |

Notification, location, alarm, full-screen jaisi permissions Firebase se nahi, phone ki **Settings → SOS & Alerts checklist** se milti hain.

## Setup (ek baar)

### 1. Firebase project
1. https://console.firebase.google.com → **Add project** (free Spark plan chalega).
2. **Add Android app** → package name `com.example` (ya pehle `applicationId` badal lo, jaise `com.yourname.cutereminder`, phir wahi daalo).
3. SHA fingerprints do (Google Sign-In + OTP ke liye zaroori):
   ```powershell
   keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   (agar `debug.keystore` project root me nahi hai to `%USERPROFILE%\.android\debug.keystore` use karo.) **SHA-1 aur SHA-256** dono Firebase → Project settings → your app me add karo.
4. `google-services.json` download karke **`app/google-services.json`** me rakho.

### 2. Login chalu karo
Firebase Console → **Authentication → Sign-in method**
- **Phone** → Enable. Testing ke liye "Phone numbers for testing" me 2 fake number + OTP daal do (free, SMS nahi lagta).
- **Google** → Enable (support email chuno). Iske baad `google-services.json` **dobara download** karke replace karo (usme web client id aata hai).

> Real SMS OTP ki free limit Firebase ke current pricing par depend karti hai; zyada users ke liye Blaze plan lag sakta hai. Pricing page check kar lo.

### 3. Database + security rules
Firebase Console → **Firestore Database → Create database** (production mode). Phir rules deploy:
```powershell
npm install -g firebase-tools
firebase login
firebase use --add            # apna project chuno
firebase deploy --only firestore:rules
```
(rules file: [firestore.rules](firestore.rules), config: [firebase.json](firebase.json))

### 3b. FCM push (app band ho tab bhi alert)
Push bhejne ke liye ek chhota server code chahiye: [functions/index.js](functions/index.js) (Cloud Function). Ye har naye event par receiver ke phone ko high-priority push bhejta hai. Iske liye Firebase project **Blaze (pay-as-you-go)** plan par hona chahiye (card lagta hai, chhote use me free quota ke andar bill ~0 aata hai; budget alert laga lena).

1. `functions/index.js` me `REGION` apne Firestore ki location jaisa rakho (Firestore → Data me location dikhti hai).
2. Deploy:
   ```powershell
   cd functions
   npm install
   cd ..
   firebase deploy --only functions
   ```
3. Logs dekhne ke liye: `firebase functions:log`

Blaze nahi lena? App phir bhi chalega: events Firestore listener se aate hain (jab tak phone service ko maar na de). Push sirf ise pakka banata hai. Dono ek saath chalein to duplicate nahi aata (event id se dedupe hota hai).

### 3c. `.env`
`.env.example` ko copy karke `.env` banao. Firebase ki keys isme **nahi** jaati (wo `google-services.json` me hain). Sirf optional `GOOGLE_WEB_CLIENT_ID` hai, jo normally khud mil jata hai.

### 4. Build
Android Studio me project **Open** karo → Sync → Run. (CLI: `gradle assembleDebug`.)

### 5. Phone par permissions (bahut zaroori)
App → **Settings → SOS & Alerts setup checklist**. Sab ✅ karo:
Notifications, Location, GPS, "Allow all the time", Full-screen alerts, Display over other apps, Battery unrestricted, Exact alarms, SMS/Call.
Phir **"Test SOS takeover screen"** dabake dekh lo ki aapke phone par kaisa dikhta hai.

## Test kaise karein

**A. Bina Firebase (2 minute):** app chalao → "Continue offline" → Settings → "Test SOS takeover screen". Receiver wali full-screen + siren dikhegi. Lock-screen test: phone lock karke dobara dabao.

**B. Poora flow (2 phones, ya 1 phone + 1 emulator):**
Emulator me **Google Play wala image** lo (Play Store icon wala), warna login/FCM nahi chalega.
1. Firebase → Authentication → Phone → "Phone numbers for testing": `+91 90000 00001` → OTP `123456`, `+91 90000 00002` → `123456`.
2. Phone-1 par number 1 se login (naam daalo). Phone-2 par number 2 se.
3. Phone-1: Personal → number 2 daalo → Add. Phone-2: Accept.
4. Phone-1: unke card par **Emergency contact** ON.
5. Phone-2 ko **lock karo** (ya app swipe-kill karo). Phone-1 par **PANIC SOS** dabao.
6. Phone-2 par screen jaagni chahiye, siren bajna chahiye. "I see it" dabao → phone-1 ko "dekh liya" notification.
7. Chat, Remind → alarm, Music (koi direct .mp3 link), Location tab bhi isi tarah dekh lo.

**C. Push kaam kar raha hai ya nahi:** phone-2 ko kill karke phone-1 se message bhejo. Na aaye to `firebase functions:log` dekho (token missing / region galat hoga) aur phone-2 par Settings checklist ✅ karo.

## Limits (sach-sach)

- **Android kisi normal app ko dusre apps "block" karne nahi deta.** SOS screen lock-screen ke upar aati hai, siren alarm-stream par full volume bajta hai, back band hai aur screen-pinning try hota hai (Android ek baar confirm puchta hai; user back+recents long-press se nikal sakta hai). Poora kiosk lock sirf device-owner/MDM se hota hai.
- Alerts **foreground service** (listener) se aate hain, bina paid server ke. Kuch phones (Xiaomi/Oppo/Vivo/Samsung) battery-saver se service maar dete hain, isliye checklist me battery "Unrestricted" aur app "Autostart" ON karo. FCM push (section 3b) isse kaafi behtar banata hai.
- Music sync dono phone ki clock par depend karta hai (1-2 sec ka fark ho sakta hai). YouTube/Spotify link direct play nahi hote.
- In-app voice/video call nahi hai (WebRTC/Agora chahiye); abhi dialer call + chat hai.
- Phone number se user dhundhne ke liye profile (naam + number) har login user ko readable hai. Location sirf accepted + share-on logon ko.
