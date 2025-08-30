"use client";
import { useEffect, useState } from "react";
import { X, RefreshCw, Clock } from "lucide-react";

interface OtpPopupProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (otp: string) => void;
  onResend: () => void;
  transactionId: number;
  ttlSeconds: number;
}

export default function OtpPopup({ isOpen, onClose, onSubmit, onResend, transactionId, ttlSeconds }: OtpPopupProps) {
  const [otp, setOtp] = useState(["", "", "", "", "", ""]);
  const [timeLeft, setTimeLeft] = useState(ttlSeconds);
  const [isResending, setIsResending] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    
    setTimeLeft(ttlSeconds);
    setOtp(["", "", "", "", "", ""]);
    
    const timer = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [isOpen, ttlSeconds]);

  useEffect(() => {
    if (timeLeft === 0) {
      // Don't auto-close, let user choose to resend or close manually
      // The transaction will be marked as expired on backend
    }
  }, [timeLeft]);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const handleOtpChange = (index: number, value: string) => {
    const val = value.replace(/[^0-9]/g, "").slice(0, 1);
    const next = [...otp];
    next[index] = val;
    setOtp(next);
    
    // Auto-focus next input
    if (val && index < 5) {
      const nextInput = document.getElementById(`otp-${index + 1}`);
      if (nextInput) nextInput.focus();
    }
    
    // Auto-submit when complete
    const joined = next.join("");
    if (joined.length === 6) {
      onSubmit(joined);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent, index: number) => {
    if (e.key === "Backspace" && !otp[index] && index > 0) {
      const prevInput = document.getElementById(`otp-${index - 1}`);
      if (prevInput) prevInput.focus();
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    const data = e.clipboardData.getData("text").replace(/[^0-9]/g, "").slice(0, 6).split("");
    const next = ["", "", "", "", "", ""];
    for (let j = 0; j < data.length; j++) next[j] = data[j];
    setOtp(next);
    
    const joined = next.join("");
    if (joined.length === 6) {
      onSubmit(joined);
    }
    e.preventDefault();
  };

  const handleResend = async () => {
    setIsResending(true);
    try {
      await onResend();
      setTimeLeft(ttlSeconds);
      setOtp(["", "", "", "", "", ""]);
    } finally {
      setIsResending(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
      <div className="bg-gray-900 border border-gray-700 rounded-2xl p-6 w-full max-w-md">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <h3 className="text-xl font-semibold text-white">OTP Verification</h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-white transition-colors"
          >
            <X className="size-5" />
          </button>
        </div>

        {/* Transaction Info */}
        <div className="mb-6 p-4 bg-gray-800 rounded-xl border border-gray-700">
          <div className="text-sm text-gray-400 mb-2">Transaction ID</div>
          <div className="text-white font-mono text-lg">{transactionId}</div>
        </div>

        {/* Timer */}
        <div className="mb-6 flex items-center justify-center gap-2 text-center">
          <Clock className="size-5 text-amber-400" />
          <div className="text-2xl font-mono font-bold text-amber-400">
            {formatTime(timeLeft)}
          </div>
        </div>

        {/* OTP Input */}
        <div className="mb-6">
          <div className="text-sm text-gray-400 mb-3 text-center">
            Enter the 6-digit OTP sent to your email
          </div>
          <div className="flex gap-3 justify-center">
            {otp.map((digit, index) => (
              <input
                key={index}
                id={`otp-${index}`}
                className="w-12 h-12 text-center text-xl font-bold bg-gray-800 border border-gray-600 rounded-lg text-white focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 transition-all"
                value={digit}
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={1}
                onChange={(e) => handleOtpChange(index, e.target.value)}
                onKeyDown={(e) => handleKeyDown(e, index)}
                onPaste={handlePaste}
                disabled={timeLeft === 0}
              />
            ))}
          </div>
        </div>

        {/* Resend Button */}
        <div className="flex justify-center">
          <button
            onClick={handleResend}
            disabled={isResending}
            className="btn bg-white/10 hover:bg-white/20 text-white disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isResending ? (
              <>
                <RefreshCw className="size-4 mr-2 animate-spin" />
                Sending...
              </>
            ) : (
              <>
                <RefreshCw className="size-4 mr-2" />
                Didn't receive the code? Resend OTP
              </>
            )}
          </button>
        </div>

        {/* Warning */}
        {timeLeft === 0 && (
          <div className="mt-4 text-center text-amber-400 text-sm">
            OTP has expired. Please click "Resend OTP" to get a new code.
          </div>
        )}
      </div>
    </div>
  );
}
