"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { Check, Crown, GraduationCap, LockKeyhole, Mail, Send, ShieldCheck, UserRound } from "lucide-react";
import { submitFeedback } from "../../../lib/backendQuizAi";

export default function SuperadminSignupPage() {
  const [form, setForm] = useState({
    name: "",
    email: "",
    reason: "",
    passcode: ""
  });
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice("");
    setError("");

    if (!form.name.trim() || !form.email.trim() || !form.reason.trim()) {
      setError("Name, email, and access reason are required.");
      return;
    }

    setLoading(true);
    try {
      await submitFeedback({
        userName: form.name.trim(),
        userEmail: form.email.trim(),
        category: "Superadmin Access Request",
        rating: 5,
        message: `Superadmin signup request. Reason: ${form.reason.trim()}${form.passcode.trim() ? ` Access code: ${form.passcode.trim()}` : ""}`
      });
      setNotice("Superadmin signup request submitted. Existing owner can review it in Support Messages.");
      setForm({ name: "", email: "", reason: "", passcode: "" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request submit failed.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app-shell support-page-shell superadmin-signup-shell">
      <div className="screen support-page-screen">
        <header className="topbar">
          <Link href="/" className="brand">
            <span className="brand-mark">
              <GraduationCap size={24} />
            </span>
            JagdiSu
          </Link>
          <div className="nav-actions">
            <Link href="/?admin=1" className="button ghost">Superadmin login</Link>
          </div>
        </header>

        <section className="support-page-layout">
          <aside className="panel panel-pad support-page-info">
            <span className="pill"><Crown size={16} /> Owner access</span>
            <h1 className="section-title">Superadmin signup</h1>
            <p className="subtitle">
              Superadmin accounts are owner-approved. Submit this request and the current owner can review it from the admin support dashboard.
            </p>
            <div className="support-page-note">
              <ShieldCheck size={20} />
              <span>Live admin login still uses secure credentials configured on the backend.</span>
            </div>
          </aside>

          <section className="panel panel-pad support-page-card">
            <h2 className="auth-title">Request access</h2>
            <form className="form-grid support-page-form" onSubmit={handleSubmit}>
              <label className="field">
                <span className="label-row">Full Name</span>
                <span className="input-icon">
                  <UserRound size={21} />
                  <input
                    className="input bare"
                    value={form.name}
                    placeholder="Full name"
                    onChange={(event) => setForm({ ...form, name: event.target.value })}
                  />
                </span>
              </label>
              <label className="field">
                <span className="label-row">Email</span>
                <span className="input-icon">
                  <Mail size={21} />
                  <input
                    className="input bare"
                    type="email"
                    value={form.email}
                    placeholder="Admin email"
                    onChange={(event) => setForm({ ...form, email: event.target.value })}
                  />
                </span>
              </label>
              <label className="field full">
                <span className="label-row">Optional Access Code</span>
                <span className="input-icon">
                  <LockKeyhole size={21} />
                  <input
                    className="input bare"
                    value={form.passcode}
                    placeholder="Owner shared code, if any"
                    onChange={(event) => setForm({ ...form, passcode: event.target.value })}
                  />
                </span>
              </label>
              <label className="field full">
                <span className="label-row">Why do you need access?</span>
                <textarea
                  className="textarea support-page-textarea"
                  value={form.reason}
                  placeholder="Write the purpose of superadmin access..."
                  onChange={(event) => setForm({ ...form, reason: event.target.value })}
                />
              </label>
              {notice ? <div className="signup-page-success field full"><Check size={18} /> {notice}</div> : null}
              {error ? <div className="error field full">{error}</div> : null}
              <button className="button primary field full" type="submit" disabled={loading}>
                <Send size={18} /> {loading ? "Submitting..." : "Submit request"}
              </button>
            </form>
          </section>
        </section>
      </div>
    </main>
  );
}
