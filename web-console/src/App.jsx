import React, { useEffect, useState } from "react";
import { initializeApp } from "firebase/app";
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signOut,
} from "firebase/auth";
import {
  collection,
  doc,
  getFirestore,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  updateDoc,
} from "firebase/firestore";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "gen-lang-client-0530773790",
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(
  app,
  import.meta.env.VITE_FIRESTORE_DATABASE_ID ||
    "ai-studio-android-diyaai-96dc77ad-8481-4285-b7fd-2dc01f2813b9"
);

export default function App() {
  const [user, setUser] = useState(null);
  const [devices, setDevices] = useState([]);
  const [commands, setCommands] = useState([]);

  useEffect(() => onAuthStateChanged(auth, setUser), []);

  useEffect(() => {
    if (!user) {
      setDevices([]);
      setCommands([]);
      return undefined;
    }
    const unsubDevices = onSnapshot(
      collection(db, "users", user.uid, "devices"),
      (snap) => setDevices(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
    );
    const unsubCommands = onSnapshot(
      query(
        collection(db, "users", user.uid, "commandRequests"),
        orderBy("createdAt", "desc")
      ),
      (snap) => setCommands(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
    );
    return () => {
      unsubDevices();
      unsubCommands();
    };
  }, [user]);

  const confirmCommand = async (cmdId, pcOnline) => {
    if (!user) return;
    await updateDoc(doc(db, "users", user.uid, "commandRequests", cmdId), {
      confirmed: true,
      status: pcOnline ? "APPROVED_QUEUED" : "PC_OFFLINE_NOT_SENT",
      updatedAt: serverTimestamp(),
    });
  };

  const pcDevice = devices.find((d) => d.deviceType === "WINDOWS_DESKTOP");
  const pcOnline = pcDevice?.connectionStatus === "ONLINE";

  return (
    <main style={{ background: "#030712", color: "#F0F6FF", minHeight: "100vh", padding: 24, fontFamily: "sans-serif" }}>
      <h1 style={{ color: "#00E5FF", letterSpacing: 3 }}>DIYA AI — REMOTE CONNECTION CONSOLE</h1>
      {!user ? (
        <button onClick={() => signInWithPopup(auth, new GoogleAuthProvider())}>
          Sign in with Google
        </button>
      ) : (
        <section>
          <p>Signed in as: {user.email || user.uid}</p>
          <button onClick={() => signOut(auth)}>Sign Out</button>
          <h2>PC Status: {pcOnline ? "ONLINE" : "OFFLINE (Waiting for Outbound Agent)"}</h2>
          <ul>
            {devices.map((d) => (
              <li key={d.id}>
                {d.deviceName} ({d.deviceType}) — {d.connectionStatus} / {d.pairingStatus}
              </li>
            ))}
          </ul>
          <h2>Command Requests</h2>
          <ul>
            {commands.map((c) => (
              <li key={c.id}>
                {c.actionType}: {c.summaryText} [{c.status}]
                {c.status === "PENDING_CONFIRMATION" && (
                  <button onClick={() => confirmCommand(c.id, pcOnline)}>
                    Confirm
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  );
}
