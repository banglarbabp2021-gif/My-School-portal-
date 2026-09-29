/**
 * Firebase Cloud Functions for School Management App
 *
 * This function triggers automatically whenever a new document is written to the
 * 'notifications' collection in Google Cloud Firestore.
 *
 * It sends Firebase Cloud Messaging (FCM) push notifications securely on the server side
 * using the Firebase Admin SDK, ensuring ZERO server keys or service account credentials
 * are exposed in the Android client app.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Triggered on creation of a notification in Firestore.
 * Resolves target FCM device tokens and dispatches push notifications.
 */
exports.onNotificationCreated = functions.firestore
  .document("notifications/{notificationId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    if (!data) return null;

    const {
      notificationId,
      title,
      body,
      message,
      targetType, // "ALL_STUDENTS", "CLASS", "STUDENT", "ALL_TEACHERS", "ALL_SCHOOL"
      targetId,
      recipientUserId,
      schoolId,
      deepLinkType = "NOTICE",
      deepLinkId = "",
      senderName = "School Administration"
    } = data;

    const notifTitle = title || "School Notice";
    const notifBody = body || message || "You have a new school notification.";
    const linkId = deepLinkId || targetId || "";

    const payloadData = {
      notificationId: String(notificationId || context.params.notificationId),
      title: String(notifTitle),
      body: String(notifBody),
      message: String(notifBody),
      targetType: String(targetType || "ALL_STUDENTS"),
      targetId: String(targetId || ""),
      recipientUserId: String(recipientUserId || ""),
      schoolId: String(schoolId || ""),
      senderName: String(senderName),
      deepLinkType: String(deepLinkType),
      deepLinkId: String(linkId),
      createdAt: String(Date.now()),
      click_action: "FLUTTER_NOTIFICATION_CLICK"
    };

    const androidConfig = {
      priority: "high",
      notification: {
        channelId: "school_notifications_channel",
        title: notifTitle,
        body: notifBody,
        sound: "default",
        icon: "ic_launcher",
        color: "#1E40AF",
        notificationPriority: "PRIORITY_HIGH",
        visibility: "PUBLIC"
      },
      data: payloadData
    };

    try {
      if (targetType === "ALL_STUDENTS") {
        // Send to topic 'all_students'
        const messagePayload = {
          topic: "all_students",
          notification: { title: notifTitle, body: notifBody },
          data: payloadData,
          android: androidConfig
        };
        const response = await admin.messaging().send(messagePayload);
        console.log("Successfully sent to all_students topic:", response);
      } else if (targetType === "ALL_TEACHERS") {
        // Send to topic 'all_teachers'
        const messagePayload = {
          topic: "all_teachers",
          notification: { title: notifTitle, body: notifBody },
          data: payloadData,
          android: androidConfig
        };
        const response = await admin.messaging().send(messagePayload);
        console.log("Successfully sent to all_teachers topic:", response);
      } else if (targetType === "CLASS") {
        // Send to specific class topic
        const sanitizedClass = (targetId || "").replace(/\s+/g, "_").toLowerCase();
        const messagePayload = {
          topic: `class_${sanitizedClass}`,
          notification: { title: notifTitle, body: notifBody },
          data: payloadData,
          android: androidConfig
        };
        const response = await admin.messaging().send(messagePayload);
        console.log(`Successfully sent to class_${sanitizedClass} topic:`, response);
      } else if (targetType === "STUDENT" || targetType === "SPECIFIC_USER") {
        // Query recipient token from fcm_tokens collection
        const targetUserId = recipientUserId || targetId;
        const tokenDoc = await admin.firestore().collection("fcm_tokens").doc(targetUserId).get();

        if (tokenDoc.exists && tokenDoc.data().fcmToken) {
          const token = tokenDoc.data().fcmToken;
          const messagePayload = {
            token: token,
            notification: { title: notifTitle, body: notifBody },
            data: payloadData,
            android: androidConfig
          };
          const response = await admin.messaging().send(messagePayload);
          console.log(`Successfully sent notification to user ${targetUserId}:`, response);
        } else {
          console.log(`No active FCM token found for user: ${targetUserId}`);
        }
      }

      // Mark the notification document as dispatched in Firestore
      await snap.ref.update({
        status: "DISPATCHED",
        dispatchedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    } catch (error) {
      console.error("Error dispatching FCM message:", error);
      await snap.ref.update({
        status: "ERROR",
        error: error.message
      });
    }

    return null;
  });
