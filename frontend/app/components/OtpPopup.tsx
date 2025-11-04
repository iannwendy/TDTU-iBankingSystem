"use client";
import { useEffect, useState } from "react";
import { RefreshCw, Clock, Minimize2, Maximize2 } from "lucide-react";

interface OtpPopupProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (otp: string) => void;
  onResend: () => void;
  transactionId: number;
  ttlSeconds: number;
  isMinimized?: boolean;
  onMinimize?: () => void;
  onMaximize?: () => void;
}

export default function OtpPopup({ isOpen, onClose, onSubmit, onResend, transactionId, ttlSeconds, isMinimized = false, onMinimize, onMaximize }: OtpPopupProps) {
  const [otp, setOtp] = useState(["", "", "", "", "", ""]);
  const [timeLeft, setTimeLeft] = useState(ttlSeconds);
  const [isResending, setIsResending] = useState(false);
  const [resendCooldownLeft, setResendCooldownLeft] = useState(0);
  const [autoCloseCountdown, setAutoCloseCountdown] = useState(0);

  useEffect(() => {
    if (!isOpen) return;
    
    // Reset timer when popup opens or transaction changes
    setTimeLeft(ttlSeconds);
    setOtp(["", "", "", "", "", ""]);
    setResendCooldownLeft(0);
    
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
  }, [isOpen, ttlSeconds, transactionId]);

  useEffect(() => {
    if (!isOpen || resendCooldownLeft <= 0) return;
    const timer = setInterval(() => {
      setResendCooldownLeft((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [isOpen, resendCooldownLeft]);

  useEffect(() => {
    if (timeLeft === 0 && isMinimized) {
      // Start auto-close countdown when OTP expires in minimized mode
      setAutoCloseCountdown(10);
      const countdown = setInterval(() => {
        setAutoCloseCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(countdown);
            onClose();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
      
      return () => clearInterval(countdown);
    } else if (timeLeft > 0) {
      // Reset auto-close countdown if timer is reset (e.g., OTP resent)
      setAutoCloseCountdown(0);
    }
  }, [timeLeft, isMinimized, onClose]);

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
    if (resendCooldownLeft > 0) return;
    setIsResending(true);
    try {
      await onResend();
      setTimeLeft(ttlSeconds);
      setOtp(["", "", "", "", "", ""]);
      setResendCooldownLeft(30);
    } finally {
      setIsResending(false);
    }
  };

  if (!isOpen) return null;

  // Minimized state - show small floating button
  if (isMinimized) {
    return (
      <div className="fixed bottom-6 right-6 z-50">
        <div className="flex flex-col items-end gap-2">
          {/* Auto-close warning when expired */}
          {timeLeft === 0 && autoCloseCountdown > 0 && (
            <div className="bg-amber-500 text-white px-4 py-2 rounded-lg shadow-lg animate-pulse">
              <div className="text-sm font-medium">OTP Expired</div>
              <div className="text-xs">Auto-closing in {autoCloseCountdown}s</div>
            </div>
          )}
          
          <button
            onClick={onMaximize}
            className={`rounded-full p-4 shadow-lg transition-all hover:scale-105 flex items-center gap-2 ${
              timeLeft === 0 
                ? "bg-red-500 hover:bg-red-600 animate-pulse" 
                : "bg-brand hover:bg-brand/90"
            } text-white`}
          >
            <Maximize2 className="size-5" />
            <span className="text-sm font-medium">
              {timeLeft === 0 ? "OTP Expired" : `OTP (${formatTime(timeLeft)})`}
            </span>
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
      <div className="bg-gray-900 border border-gray-700 rounded-2xl p-6 w-full max-w-md">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <h3 className="text-xl font-semibold text-white">OTP Verification</h3>
          <div className="flex items-center gap-2">
            <button
              onClick={onMinimize}
              className="text-gray-400 hover:text-white transition-colors"
              title="Thu nhỏ"
            >
              <Minimize2 className="size-5" />
            </button>
          </div>
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
            disabled={isResending || resendCooldownLeft > 0}
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

        {/* Warnings */}
        <div className="mt-4 text-center text-amber-400 text-sm">
          You can resend OTP up to 3 times. Please wait at least 30 seconds between resends.
          {resendCooldownLeft > 0 && (
            <div className="mt-1">Please wait {resendCooldownLeft}s before resending.</div>
          )}
        </div>
        {timeLeft === 0 && (
          <div className="mt-2 text-center text-amber-400 text-sm">
            OTP has expired. Please click "Resend OTP" to get a new code.
          </div>
        )}
      </div>
    </div>
  );
}
