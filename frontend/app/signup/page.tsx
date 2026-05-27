"use client";

import Link from "next/link";
import { FormEvent, ReactNode, useEffect, useMemo, useRef, useState } from "react";
import {
  BarChart3,
  BookOpen,
  BrainCircuit,
  Check,
  ChevronDown,
  Eye,
  EyeOff,
  GraduationCap,
  LockKeyhole,
  Mail,
  Phone,
  ShieldCheck,
  Sparkles,
  UserRound,
  UsersRound
} from "lucide-react";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:2000/api";
const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "";

type GoogleCredentialResponse = {
  credential?: string;
};

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: { client_id: string; callback: (response: GoogleCredentialResponse) => void }) => void;
          renderButton: (element: HTMLElement, options: Record<string, string | number | boolean>) => void;
        };
      };
    };
  }
}

type FormState = {
  fullName: string;
  email: string;
  mobile: string;
  password: string;
  confirmPassword: string;
  role: string;
  terms: boolean;
};

type FieldName = keyof FormState;

const initialForm: FormState = {
  fullName: "",
  email: "",
  mobile: "",
  password: "",
  confirmPassword: "",
  role: "Student",
  terms: false
};

const features = [
  { title: "AI quiz practice", icon: BrainCircuit, copy: "Generate exam-ready questions and explanations." },
  { title: "Progress tracking", icon: BarChart3, copy: "Review recent attempts and weak topics." },
  { title: "Study notes", icon: BookOpen, copy: "Create quick revision notes for any topic." }
];

export default function SignupPage() {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [form, setForm] = useState<FormState>(initialForm);
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({});
  const [submitted, setSubmitted] = useState(false);
  const [signupLoading, setSignupLoading] = useState(false);
  const [signupError, setSignupError] = useState("");
  const googleButtonRef = useRef<HTMLDivElement | null>(null);

  const isValid = useMemo(() => Object.keys(validate(form)).length === 0, [form]);

  useEffect(() => {
    if (!googleButtonRef.current || !GOOGLE_CLIENT_ID) {
      return;
    }

    function renderGoogleButton() {
      if (!window.google || !googleButtonRef.current) {
        return;
      }
      googleButtonRef.current.innerHTML = "";
      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: (response) => {
          void signupWithGoogle(response.credential);
        }
      });
      window.google.accounts.id.renderButton(googleButtonRef.current, {
        theme: "outline",
        size: "large",
        width: 320,
        text: "continue_with",
        shape: "rectangular",
        logo_alignment: "left"
      });
    }

    if (window.google) {
      renderGoogleButton();
      return;
    }

    const existingScript = document.querySelector<HTMLScriptElement>("script[src='https://accounts.google.com/gsi/client']");
    if (existingScript) {
      existingScript.addEventListener("load", renderGoogleButton, { once: true });
      return () => existingScript.removeEventListener("load", renderGoogleButton);
    }

    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.addEventListener("load", renderGoogleButton, { once: true });
    document.head.appendChild(script);

    return () => script.removeEventListener("load", renderGoogleButton);
  }, []);

  function updateField(name: FieldName, value: string | boolean) {
    setForm((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined }));
    setSubmitted(false);
    setSignupError("");
  }

  function validate(values: FormState) {
    const nextErrors: Partial<Record<FieldName, string>> = {};
    if (values.fullName.trim().length < 2) nextErrors.fullName = "Enter your full name.";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) nextErrors.email = "Enter a valid email.";
    if (!/^\d{10,15}$/.test(values.mobile.replace(/\D/g, ""))) nextErrors.mobile = "Enter a valid mobile number.";
    if (values.password.length < 8) nextErrors.password = "Use at least 8 characters.";
    if (values.confirmPassword !== values.password) nextErrors.confirmPassword = "Passwords do not match.";
    if (!values.role) nextErrors.role = "Select a role.";
    if (!values.terms) nextErrors.terms = "Accept the terms to continue.";
    return nextErrors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validate(form);
    setErrors(nextErrors);
    setSubmitted(false);
    setSignupError("");

    if (Object.keys(nextErrors).length > 0 || signupLoading) {
      return;
    }

    setSignupLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/auth/signup`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: form.fullName.trim(),
          username: form.fullName.trim(),
          email: form.email.trim(),
          password: form.password
        })
      });

      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || body.error || "Signup failed. Please try again.");
      }

      const user = await response.json();
      window.localStorage.setItem("jdsu-user", JSON.stringify(user));
      window.localStorage.setItem("jdsu-plan", user.plan ?? "FREE");
      setSubmitted(true);
      window.location.href = "/";
    } catch (err) {
      const message = err instanceof TypeError
        ? "Backend is not reachable. Please start the Spring Boot server on port 2000 and check MySQL."
        : err instanceof Error
          ? err.message
          : "Signup failed. Please try again.";
      setSignupError(message);
    } finally {
      setSignupLoading(false);
    }
  }

  async function signupWithGoogle(credential?: string) {
    setSignupError("");
    if (!GOOGLE_CLIENT_ID) {
      setSignupError("Google login setup missing hai. frontend/.env.local me NEXT_PUBLIC_GOOGLE_CLIENT_ID add karo.");
      return;
    }
    if (!credential) {
      setSignupError("Google login credential nahi mila. Please try again.");
      return;
    }

    setSignupLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/auth/google`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ credential })
      });

      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || body.error || "Google login failed.");
      }

      const user = await response.json();
      window.localStorage.setItem("jdsu-user", JSON.stringify(user));
      window.localStorage.setItem("jdsu-plan", user.plan ?? "FREE");
      window.location.href = "/";
    } catch (err) {
      setSignupError(err instanceof Error ? err.message : "Google login failed.");
    } finally {
      setSignupLoading(false);
    }
  }

  return (
    <main className="app-shell signup-page-shell">
      <div className="screen signup-page-screen">
        <header className="topbar">
          <Link href="/" className="brand">
            <span className="brand-mark">
              <GraduationCap size={24} />
            </span>
            JagdiSu
          </Link>
          <div className="nav-actions">
            <Link href="/" className="button ghost">
              Login
            </Link>
          </div>
        </header>

        <section className="signup-page-layout">
          <aside className="panel panel-pad signup-page-info">
            <span className="pill">
              <Sparkles size={16} /> Student workspace
            </span>
            <h1 className="section-title">Create your JagdiSu account</h1>
            <p className="subtitle">
              Join the same learning workspace used for AI quizzes, topic notes, handwritten answer review, and progress tracking.
            </p>

            <div className="signup-page-feature-grid">
              {features.map((feature) => {
                const Icon = feature.icon;
                return (
                  <div className="signup-page-feature" key={feature.title}>
                    <span>
                      <Icon size={22} />
                    </span>
                    <div>
                      <strong>{feature.title}</strong>
                      <p>{feature.copy}</p>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="quota-strip signup-page-note">
              <ShieldCheck size={22} />
              <div>
                <strong>Secure account setup</strong>
                <span>Your learning history and plan details stay connected to your account.</span>
              </div>
            </div>
          </aside>

          <section className="panel panel-pad signup-page-card">
            <div className="signup-page-card-head">
              <span className="login-card-logo compact-logo">
                <GraduationCap size={34} />
                <BookOpen size={25} />
              </span>
              <div>
                <h2 className="auth-title">Sign up</h2>
                <p className="subtitle">Start learning with JagdiSu AI today.</p>
              </div>
            </div>

            <form className="form-grid signup-page-form" onSubmit={handleSubmit} noValidate>
              <SignupField
                icon={<UserRound size={21} />}
                label="Full Name"
                name="fullName"
                placeholder="Full Name"
                value={form.fullName}
                error={errors.fullName}
                autoComplete="name"
                onChange={(value) => updateField("fullName", value)}
              />
              <SignupField
                icon={<Mail size={21} />}
                label="Email"
                name="email"
                type="email"
                placeholder="Email"
                value={form.email}
                error={errors.email}
                autoComplete="email"
                onChange={(value) => updateField("email", value)}
              />
              <SignupField
                icon={<Phone size={21} />}
                label="Mobile Number"
                name="mobile"
                placeholder="Mobile Number"
                value={form.mobile}
                error={errors.mobile}
                inputMode="tel"
                autoComplete="tel"
                onChange={(value) => updateField("mobile", value)}
              />

              <label className="field">
                <span className="label-row">Role</span>
                <span className="input-icon">
                  <UsersRound size={21} />
                  <select
                    name="role"
                    value={form.role}
                    aria-invalid={Boolean(errors.role)}
                    onChange={(event) => updateField("role", event.target.value)}
                    className="select bare-select"
                  >
                    <option value="Student">Student</option>
                    <option value="Teacher">Teacher</option>
                    <option value="Admin">Admin</option>
                  </select>
                  <ChevronDown size={18} />
                </span>
                {errors.role ? <span className="signup-page-error-text">{errors.role}</span> : null}
              </label>

              <SignupField
                icon={<LockKeyhole size={21} />}
                label="Password"
                name="password"
                type={showPassword ? "text" : "password"}
                placeholder="Password"
                value={form.password}
                error={errors.password}
                autoComplete="new-password"
                onChange={(value) => updateField("password", value)}
                action={
                  <button
                    type="button"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    onClick={() => setShowPassword((value) => !value)}
                    className="text-link signup-page-icon-button"
                  >
                    {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                  </button>
                }
              />
              <SignupField
                icon={<LockKeyhole size={21} />}
                label="Confirm Password"
                name="confirmPassword"
                type={showConfirmPassword ? "text" : "password"}
                placeholder="Confirm Password"
                value={form.confirmPassword}
                error={errors.confirmPassword}
                autoComplete="new-password"
                onChange={(value) => updateField("confirmPassword", value)}
                action={
                  <button
                    type="button"
                    aria-label={showConfirmPassword ? "Hide confirm password" : "Show confirm password"}
                    onClick={() => setShowConfirmPassword((value) => !value)}
                    className="text-link signup-page-icon-button"
                  >
                    {showConfirmPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                  </button>
                }
              />

              <label className="remember signup-page-terms field full">
                <input
                  type="checkbox"
                  checked={form.terms}
                  onChange={(event) => updateField("terms", event.target.checked)}
                  aria-invalid={Boolean(errors.terms)}
                />
                <span>
                  I agree to the <button type="button" className="text-link">Terms and Conditions</button>
                </span>
              </label>
              {errors.terms ? <span className="signup-page-error-text field full">{errors.terms}</span> : null}

              {submitted ? (
                <div className="signup-page-success field full">
                  <Check size={18} /> Account details look good. Signup flow is ready to connect to your API.
                </div>
              ) : null}
              {signupError ? <div className="error field full">{signupError}</div> : null}

              <div className="field full signup-page-actions">
                <button className="button primary" type="submit" disabled={signupLoading || (!isValid && submitted)}>
                  <UserRound size={18} /> {signupLoading ? "Creating account..." : "Signup"}
                </button>
                {GOOGLE_CLIENT_ID ? (
                  <div className="google-login-slot" ref={googleButtonRef} />
                ) : (
                  <button className="button google" type="button" disabled={signupLoading} onClick={() => signupWithGoogle()}>
                    <span className="google-mark">G</span> Continue with Google
                  </button>
                )}
              </div>

              <p className="signup-page-switch field full">
                Already have an account? <Link href="/" className="text-link">Login</Link>
              </p>
            </form>
          </section>
        </section>
      </div>
    </main>
  );
}

function SignupField({
  icon,
  label,
  name,
  value,
  placeholder,
  error,
  action,
  onChange,
  type = "text",
  inputMode,
  autoComplete
}: {
  icon: ReactNode;
  label: string;
  name: string;
  value: string;
  placeholder: string;
  error?: string;
  action?: ReactNode;
  onChange: (value: string) => void;
  type?: string;
  inputMode?: "text" | "email" | "tel" | "url" | "numeric" | "decimal" | "search";
  autoComplete?: string;
}) {
  return (
    <label className="field">
      <span className="label-row">{label}</span>
      <span className="input-icon">
        {icon}
        <input
          name={name}
          type={type}
          value={value}
          placeholder={placeholder}
          inputMode={inputMode}
          autoComplete={autoComplete}
          aria-invalid={Boolean(error)}
          onChange={(event) => onChange(event.target.value)}
          className="input bare"
        />
        {action ? <span className="signup-page-field-action">{action}</span> : null}
      </span>
      {error ? <span className="signup-page-error-text">{error}</span> : null}
    </label>
  );
}
