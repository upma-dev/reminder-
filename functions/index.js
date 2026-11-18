const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();

// IMPORTANT: use the same region as your Firestore database
// (Firebase Console -> Firestore -> Data -> the location shown at the top).
// India: "asia-south1" (Mumbai). US multi-region: "us-central1". Europe: "europe-west1".
const REGION = "asia-south1";

/**
 * Every new document in events/{id} is pushed to the receiver's phone as a
 * high-priority FCM DATA message. The app (PushService) turns it into the SOS takeover,
 * chat notification, alarm, etc. even when it is closed.
 */
exports.pushEvent = onDocumentCreated({ document: "events/{eventId}", region: REGION }, async (event) => {
  const d = event.data && event.data.data();
  if (!d || !d.to) return;

  const tokenSnap = await admin.firestore().doc(`tokens/${d.to}`).get();
  const token = tokenSnap.exists ? tokenSnap.data().token : null;
  if (!token) {
    logger.info("no push token for receiver; Firestore listener will deliver instead", { to: d.to });
    return;
  }

  const str = (v) => (v === undefined || v === null ? "" : String(v));
  const urgent = d.type === "SOS" || d.type === "SOS_SAFE";

  const message = {
    token,
    // DATA only: no "notification" block, so the app fully controls what is shown.
    data: {
      eventId: event.params.eventId,
      type: str(d.type),
      from: str(d.from),
      fromName: str(d.fromName),
      fromPhone: str(d.fromPhone),
      title: str(d.title),
      body: str(d.body),
      lat: str(d.lat),
      lng: str(d.lng),
      hour: str(d.hour),
      minute: str(d.minute),
      extra: str(d.extra),
      ts: str(d.ts),
    },
    android: {
      priority: "high",
      // SOS is useless when late: drop after 10 min. Everything else may wait a day.
      ttl: urgent ? 10 * 60 * 1000 : 24 * 60 * 60 * 1000,
    },
  };

  try {
    await admin.messaging().send(message);
  } catch (err) {
    logger.error("FCM send failed", err);
    if (err.code === "messaging/registration-token-not-registered") {
      await admin.firestore().doc(`tokens/${d.to}`).delete();
    }
  }
});
