"use client";
import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { Toaster, toast } from "react-hot-toast";
import { Loader2, LogIn, Send, ShieldCheck, User2, Wallet } from "lucide-react";
import OtpPopup from "./components/OtpPopup";

const API = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

export default function Page() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [me, setMe] = useState<any>(null);

  const [studentId, setStudentId] = useState("");
  const [tuition, setTuition] = useState<any>(null);
  const [transactionId, setTransactionId] = useState<number | null>(null);
  const [otp, setOtp] = useState(["", "", "", "", "", ""]);
  const [otpPopupOpen, setOtpPopupOpen] = useState(false);
  const [otpPopupMinimized, setOtpPopupMinimized] = useState(false);
  const [otpTtlSeconds, setOtpTtlSeconds] = useState(120);
  const [history, setHistory] = useState<any[] | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [showTerms, setShowTerms] = useState(false);

  const formatVND = (value: any) => {
    const n = Number(value ?? 0);
    return new Intl.NumberFormat("vi-VN").format(n) + " VND";
  };
  const formatDate = (iso?: string) => iso ? new Date(iso).toLocaleString("vi-VN") : "";

  useEffect(() => {
    if (studentId && studentId.length === 8) {
      lookupTuitionBy(studentId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studentId, token]);

  // Poll transaction status to auto-unlock when transaction expires/fails
  useEffect(() => {
    if (!transactionId || !token) return;
    
    const checkTransactionStatus = async () => {
      try {
        const res = await axios.get(`${API}/api/payment/history`, { headers: { Authorization: `Bearer ${token}` } });
        const txn = res.data?.find((t: any) => t.id === transactionId);
        
        if (!txn || (txn.status !== 'PENDING_OTP' && txn.status !== 'PROCESSING')) {
          // Transaction is no longer pending (expired, failed, or completed)
          setTransactionId(null);
          setOtpPopupOpen(false);
          setOtpPopupMinimized(false);
          setOtp(["", "", "", "", "", ""]);
          if (txn?.status === 'FAILED' || txn?.status === 'EXPIRED') {
            toast.error("The pending transaction has expired or failed. You can now create a new transaction.");
          }
        }
      } catch (err) {
        console.log("Failed to check transaction status:", err);
      }
    };
    
    // Check every 5 seconds
    const interval = setInterval(checkTransactionStatus, 5000);
    
    return () => clearInterval(interval);
  }, [transactionId, token]);

  useEffect(() => {
    try {
      const raw = localStorage.getItem("auth");
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed?.token && parsed?.me) {
          setToken(parsed.token);
          // always sync from server to avoid stale balance
          axios.get(`${API}/api/auth/me`, { headers: { Authorization: `Bearer ${parsed.token}` } })
            .then((res) => {
              const synced = { ...parsed.me, ...res.data };
              setMe(synced);
              localStorage.setItem("auth", JSON.stringify({ token: parsed.token, me: synced }));
            })
            .catch(() => setMe(parsed.me));
        }
      }
    } catch {}
  }, []);

  function persistAuth(nextToken: string, nextMe: any) {
    setToken(nextToken);
    setMe(nextMe);
    // Reset all UI states when logging in
    setHistoryOpen(false);
    setHistory(null);
    setStudentId("");
    setTuition(null);
    setTransactionId(null);
    setOtp(["", "", "", "", "", ""]);
    setOtpPopupOpen(false);
    setOtpPopupMinimized(false);
    setAgreeTerms(false);
    setShowTerms(false);
    try { localStorage.setItem("auth", JSON.stringify({ token: nextToken, me: nextMe })); } catch {}
  }

  function logout() {
    setToken(null);
    setMe(null);
    setTransactionId(null);
    setTuition(null);
    setOtp(["", "", "", "", "", ""]);
    setHistoryOpen(false);
    setHistory(null);
    setStudentId("");
    setAgreeTerms(false);
    setShowTerms(false);
    setOtpPopupOpen(false);
    setOtpPopupMinimized(false);
    try { localStorage.removeItem("auth"); } catch {}
    toast.success("Logged out successfully");
  }

  async function handleLogin(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await axios.post(`${API}/api/auth/login`, { username, password });
      const nextMe = { fullName: res.data.fullName, phone: res.data.phone, email: res.data.email, balance: res.data.balance };
      
      // If there's a pending transaction, restore it
      if (res.data.pendingTransactionId) {
        setTransactionId(res.data.pendingTransactionId);
        setOtpTtlSeconds(120); // Default TTL, will be updated when we check transaction
        setOtpPopupOpen(true);
        setOtpPopupMinimized(false);
        toast.success("Login successful. You have a pending OTP transaction.");
        
        // Try to get transaction details to calculate remaining time
        try {
          const txnRes = await axios.get(`${API}/api/payment/history`, { headers: { Authorization: `Bearer ${res.data.token}` } });
          const pendingTxn = txnRes.data?.find((t: any) => t.id === res.data.pendingTransactionId);
          if (pendingTxn && pendingTxn.createdAt) {
            const createdAt = new Date(pendingTxn.createdAt);
            const now = new Date();
            const elapsed = Math.floor((now.getTime() - createdAt.getTime()) / 1000);
            const remaining = Math.max(0, 120 - elapsed); // 120s TTL
            setOtpTtlSeconds(remaining);
          }
        } catch (err) {
          console.log("Could not fetch transaction details:", err);
        }
      }
      
      persistAuth(res.data.token, nextMe);
      if (!res.data.pendingTransactionId) {
        toast.success("Login successful");
      }
    } catch (e: any) {
      toast.error(e?.response?.data?.message || "Login failed");
    } finally {
      setLoading(false);
    }
  }

  async function lookupTuitionBy(value: string) {
    setTuition(null);
    if (value.length !== 8) return;
    try {
      const res = await axios.get(`${API}/api/tuition/lookup`, { params: { studentId: value.toUpperCase() }, headers: { Authorization: `Bearer ${token}` } });
      setTuition(res.data);
    } catch (e: any) {
      // silent when typing
    }
  }

  async function initiatePayment() {
    try {
      // If there's an existing transaction, try to resend OTP first
      if (transactionId) {
        try {
          const resendRes = await axios.post(`${API}/api/payment/resend-otp`, { transactionId }, { headers: { Authorization: `Bearer ${token}` } });
          setOtpTtlSeconds(resendRes.data.ttlSeconds);
          setOtpPopupOpen(true);
          setOtpPopupMinimized(false);
          toast.success(`New OTP sent to your email. Expires in ${resendRes.data.ttlSeconds}s`);
          return;
        } catch (resendError: any) {
          // If resend fails, show error and restore popup
          toast.error(resendError?.response?.data?.message || "Failed to resend OTP");
          setOtpPopupOpen(true);
          setOtpPopupMinimized(false);
          return;
        }
      }
      
      // Create new transaction
      const res = await axios.post(`${API}/api/payment/initiate`, { studentId }, { headers: { Authorization: `Bearer ${token}` } });
      setTransactionId(res.data.transactionId);
      setOtpTtlSeconds(res.data.ttlSeconds);
      setOtpPopupOpen(true);
      setOtpPopupMinimized(false);
      toast.success(`OTP sent to your email. Expires in ${res.data.ttlSeconds}s`);
    } catch (e: any) {
      const errorMessage = e?.response?.data?.message || "Failed to initiate transaction";
      
      // If backend rejects because of pending transaction, try to restore it
      if (e?.response?.status === 409 && errorMessage.includes("pending payment transaction")) {
        // Extract transaction ID from error message (format: "ID: XXX")
        const match = errorMessage.match(/ID:\s*(\d+)/);
        if (match) {
          const existingTxnId = parseInt(match[1], 10);
          setTransactionId(existingTxnId);
          setOtpTtlSeconds(120); // Reset to default TTL
          setOtpPopupOpen(true);
          setOtpPopupMinimized(false);
          toast.error("You have a pending OTP transaction. Please complete the current transaction first.");
          
          // Try to resend OTP for existing transaction
          try {
            const resendRes = await axios.post(`${API}/api/payment/resend-otp`, { transactionId: existingTxnId }, { headers: { Authorization: `Bearer ${token}` } });
            setOtpTtlSeconds(resendRes.data.ttlSeconds);
            toast.success(`OTP has been resent to your email. Expires in ${resendRes.data.ttlSeconds}s`);
          } catch (resendErr: any) {
            // If resend fails, still show popup but user can manually resend
            console.log("Failed to auto-resend OTP:", resendErr);
          }
          return;
        }
      }
      
      toast.error(errorMessage);
    }
  }

  async function submitOtp(value: string) {
    try {
      const res = await axios.post(`${API}/api/payment/confirm`, { transactionId, otp: value }, { headers: { Authorization: `Bearer ${token}` } });
      toast.success(res.data.message || "Payment successful");
      // update local balances and tuition state
      const paidAmount = Number(tuition?.amount || 0);
      const updatedMe = me ? { ...me, balance: Math.max(0, Number(me.balance) - paidAmount) } : me;
      if (updatedMe && token) persistAuth(token, updatedMe);
      setMe(updatedMe);
      setTuition((prev: any) => prev ? { ...prev, amount: 0, paid: true } : prev);
      setOtp(["", "", "", "", "", ""]);
      setTransactionId(null);
      setOtpPopupOpen(false);
      setOtpPopupMinimized(false);
    } catch (e: any) {
      toast.error(e?.response?.data?.message || "Invalid OTP");
    }
  }

  async function loadHistory() {
    setHistoryLoading(true);
    try {
      const res = await axios.get(`${API}/api/payment/history`, { headers: { Authorization: `Bearer ${token}` } });
      setHistory(res.data || []);
      setHistoryOpen(true);
    } catch (e: any) {
      toast.error("Failed to load transaction history");
    } finally {
      setHistoryLoading(false);
    }
  }

  async function resendOtp() {
    try {
      const res = await axios.post(`${API}/api/payment/resend-otp`, { transactionId }, { headers: { Authorization: `Bearer ${token}` } });
      setOtpTtlSeconds(res.data.ttlSeconds);
      toast.success(`New OTP sent to your email. Expires in ${res.data.ttlSeconds}s`);
      return Promise.resolve();
    } catch (e: any) {
      toast.error(e?.response?.data?.message || "Failed to resend OTP");
      return Promise.reject(e);
    }
  }

  function minimizeOtpPopup() {
    setOtpPopupMinimized(true);
  }

  function maximizeOtpPopup() {
    setOtpPopupMinimized(false);
  }

  function closeOtpPopup() {
    setOtpPopupOpen(false);
    setOtpPopupMinimized(false);
  }

  return (
    <div className="mx-auto max-w-5xl p-6 md:p-10">
      <Toaster position="top-center" />
      <div className="mb-8 flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <ShieldCheck className="size-7 text-brand" />
          <h1 className="text-2xl md:text-3xl font-semibold tracking-tight">iBanking Tuition Payment</h1>
        </div>
        {token && me && (
          <div className="flex items-center gap-3">
            <div className="hidden md:block text-white/70">{me.fullName}</div>
            <button className="btn btn-sm bg-white/10 hover:bg-white/20 text-white" onClick={loadHistory} disabled={historyLoading}>{historyLoading ? 'Loading...' : 'Transaction history'}</button>
            <button className="btn btn-sm bg-white/10 hover:bg-white/20 text-white" onClick={logout}>Logout</button>
          </div>
        )}
      </div>

      {!token ? (
        <div className="min-h-[65vh] grid place-items-center">
        <form onSubmit={handleLogin} className="card p-6 md:p-8 w-full max-w-md">
          <div className="flex items-center gap-2 mb-4 text-white/80">
            <LogIn className="size-5" />
            <div className="font-medium">Login</div>
          </div>
          <div className="grid gap-3">
            <input className="input" placeholder="Username" value={username} onChange={(e) => setUsername(e.target.value)} />
            <input className="input" placeholder="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            <button className="btn mt-2" type="submit" disabled={loading}>
              {loading ? (<><Loader2 className="size-4 mr-2 animate-spin"/>Logging in...</>) : (<>Login</>)}
            </button>
          </div>
        </form>
        </div>
      ) : me && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <section className="card p-6 md:p-8">
            <div className="flex items-center gap-2 mb-4 text-white/80">
              <User2 className="size-5" />
              <div className="font-medium">Payer Information</div>
            </div>
            <div className="grid gap-2 text-white/90">
              <div className="flex justify-between"><span>Full name</span><b>{me.fullName}</b></div>
              <div className="flex justify-between"><span>Phone</span><b>{me.phone}</b></div>
              <div className="flex justify-between"><span>Email</span><b>{me.email}</b></div>
            </div>
          </section>

          <section className="card p-6 md:p-8">
            <div className="flex items-center gap-2 mb-4 text-white/80">
              <Wallet className="size-5" />
              <div className="font-medium">Tuition</div>
            </div>
            <div className="flex gap-3">
              <input 
                className="input" 
                placeholder="MSSV (8 chars)" 
                value={studentId} 
                disabled={!!transactionId}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                  const v = e.target.value.slice(0, 8);
                  setStudentId(v);
                  if (v.length === 8) lookupTuitionBy(v);
                }} 
              />
              <button 
                className="btn" 
                disabled={!!transactionId}
                onClick={() => lookupTuitionBy(studentId)}
              >
                <Send className="size-4 mr-2"/>Lookup
              </button>
            </div>
            {transactionId && (
              <div className="mt-2 text-amber-400 text-sm">
                ⚠️ You have a pending OTP transaction (ID: {transactionId}). Please complete this transaction before creating a new one.
              </div>
            )}
            {tuition && (
              <div className="mt-4 grid gap-2 text-white/90">
                <div className="flex justify-between"><span>Student</span><b>{tuition.studentName}</b></div>
                <div className="flex justify-between"><span>Semester</span><b>{tuition.semester}</b></div>
                <div className="flex justify-between"><span>Amount</span><b>{formatVND(tuition.amount)}</b></div>
                {tuition.paid && <div className="text-emerald-400">Tuition has been paid</div>}
              </div>
            )}
          </section>

          <section className="card p-6 md:p-8 md:col-span-2">
            <div className="flex items-center gap-2 mb-4 text-white/80">
              <ShieldCheck className="size-5" />
              <div className="font-medium">Payment</div>
            </div>
            <div className="flex items-center justify-between mb-2 text-white/90">
              <div>Available balance</div>
              <b>{formatVND(me.balance)}</b>
            </div>
            {tuition && (
              <>
                <div className="flex items-center justify-between mb-2 text-white/90">
                  <div>Will deduct</div>
                  <b>{formatVND(tuition.amount)}</b>
                </div>
                <div className="flex items-center justify-between mb-4 text-white/90">
                  <div>Remaining balance</div>
                  <b>{formatVND(Math.max(0, Number(me.balance) - Number(tuition.amount || 0)))} </b>
                </div>
                {Number(tuition.amount) > Number(me.balance) && !tuition.paid && (
                  <div className="mb-4 text-amber-400">Insufficient balance to pay tuition</div>
                )}
                <div className="mb-3">
                  <label className="inline-flex items-start gap-2 select-none">
                    <input type="checkbox" className="mt-1" checked={agreeTerms} onChange={(e) => setAgreeTerms(e.target.checked)} />
                    <span className="text-white/80">I have read and agree to the system Terms & Conditions</span>
                  </label>
                  <button type="button" className="ml-3 text-brand underline hover:opacity-80" onClick={() => setShowTerms((v) => !v)}>
                    {showTerms ? 'Hide terms' : 'View terms'}
                  </button>
                  {showTerms && (
                    <div className="mt-3 rounded-xl border border-white/10 bg-white/5 p-4 text-sm text-white/80 space-y-2">
                      <div className="font-medium text-white">Terms & Conditions</div>
                      <ul className="list-disc pl-5 space-y-1">
                        <li>Tuition amount and student details are retrieved from the university tuition service at the time of lookup.</li>
                        <li>By confirming, you authorize iBanking to debit the specified amount from your account balance and transfer it to the university.</li>
                        <li>An OTP sent to your registered email is required to finalize the transaction. Do not share this OTP with anyone.</li>
                        <li>Payments are processed in VND. Successful transactions are recorded in Transaction history with timestamp and status.</li>
                        <li>If your balance is insufficient, the transaction cannot be initiated.</li>
                        <li>In case of network or system errors, the transaction may be cancelled or refunded automatically according to bank policy.</li>
                        <li>Your personal information is used solely for authentication and payment processing in this system.</li>
                      </ul>
                    </div>
                  )}
                </div>
              </>
            )}
            <button 
              className="btn" 
              disabled={!tuition || tuition.paid || Number(tuition.amount) === 0 || Number(tuition.amount) > Number(me.balance) || !agreeTerms || !!transactionId} 
              onClick={initiatePayment}
            >
              {transactionId ? "Waiting for OTP..." : "Confirm transaction"}
            </button>
          </section>



          {historyOpen && (
            <section className="card p-6 md:p-8 md:col-span-2">
              <div className="flex items-center justify-between mb-4 text-white/80">
                <div className="font-medium">Transaction history</div>
                <button className="btn bg-white/10 hover:bg-white/20 text-white" onClick={() => setHistoryOpen(false)}>Close</button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="text-white/60">
                    <tr>
                      <th className="text-left py-2">Time</th>
                      <th className="text-left py-2">Student ID</th>
                      <th className="text-left py-2">Semester</th>
                      <th className="text-right py-2">Amount</th>
                      <th className="text-left py-2 pl-8">Status</th>
                    </tr>
                  </thead>
                  <tbody className="text-white/90">
                    {(history || []).map((h) => (
                      <tr key={h.id} className="border-t border-white/10">
                        <td className="py-2">{formatDate(h.completedAt || h.createdAt)}</td>
                        <td className="py-2">{h.studentId}</td>
                        <td className="py-2">{h.semester}</td>
                        <td className="py-2 text-right">{formatVND(h.amount)}</td>
                        <td className="py-2 pl-8">{h.status}</td>
                      </tr>
                    ))}
                    {(!history || history.length === 0) && (
                      <tr><td className="py-3 text-white/60" colSpan={5}>No transactions</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </div>
      )}

      {/* OTP Popup */}
      <OtpPopup
        isOpen={otpPopupOpen}
        onClose={closeOtpPopup}
        onSubmit={submitOtp}
        onResend={resendOtp}
        transactionId={transactionId || 0}
        ttlSeconds={otpTtlSeconds}
        isMinimized={otpPopupMinimized}
        onMinimize={minimizeOtpPopup}
        onMaximize={maximizeOtpPopup}
      />
    </div>
  );
}


