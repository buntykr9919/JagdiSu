"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { Check, GraduationCap, Mail, MessageCircle, Send, UserRound } from "lucide-react";
import { submitFeedback } from "../../lib/backendQuizAi";

export default function ComplaintPage() {
  const [form, setForm] = useState({
    name: "",
    email: "",
    category: "Complaint",
    message: ""
  });
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice("");
    setError("");

    if (!form.message.trim()) {
      setError("Please write your complaint.");
      return;
    }

    setLoading(true);
    try {
      await submitFeedback({
        userName: form.name.trim() || null,
        userEmail: form.email.trim() || null,
        category: form.category,
        rating: 3,
        message: form.message.trim()
      });
      setNotice("Complaint submitted. Our team will review it soon.");
      setForm({ name: "", email: "", category: "Complaint", message: "" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Complaint submit failed.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app-shell support-page-shell">
      <div className="screen support-page-screen">
        <header className="topbar">
          <Link href="/" className="brand">
            <span className="brand-mark">
              <GraduationCap size={24} />
            </span>
            JagdiSu
          </Link>
          <Link href="/" className="button ghost">Back to login</Link>
        </header>

        <section className="support-page-layout">
          <aside className="panel panel-pad support-page-info">
            <span className="pill"><MessageCircle size={16} /> Help center</span>
            <h1 className="section-title">Submit a complaint</h1>
            <p className="subtitle">
              Share the issue you are facing with login, quiz generation, payment, or account access.
            </p>
            <div className="support-page-note">
              <Check size={20} />
              <span>Complaints appear in the Superadmin support dashboard.</span>
            </div>
          </aside>

          <section className="panel panel-pad support-page-card">
            <h2 className="auth-title">Complaint form</h2>
            <form className="form-grid support-page-form" onSubmit={handleSubmit}>
              <label className="field">
                <span className="label-row">Name</span>
                <span className="input-icon">
                  <UserRound size={21} />
                  <input
                    className="input bare"
                    value={form.name}
                    placeholder="Your name"
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
                    placeholder="Email address"
                    onChange={(event) => setForm({ ...form, email: event.target.value })}
                  />
                </span>
              </label>
              <label className="field full">
                <span className="label-row">Issue type</span>
                <select
                  className="select"
                  value={form.category}
                  onChange={(event) => setForm({ ...form, category: event.target.value })}
                >
                  <option>Complaint</option>
                  <option>Login Issue</option>
                  <option>Payment Issue</option>
                  <option>Quiz Issue</option>
                  <option>Account Access</option>
                </select>
              </label>
              <label className="field full">
                <span className="label-row">Message</span>
                <textarea
                  className="textarea support-page-textarea"
                  value={form.message}
                  placeholder="Write your complaint here..."
                  onChange={(event) => setForm({ ...form, message: event.target.value })}
                />
              </label>
              {notice ? <div className="signup-page-success field full"><Check size={18} /> {notice}</div> : null}
              {error ? <div className="error field full">{error}</div> : null}
              <button className="button primary field full" type="submit" disabled={loading}>
                <Send size={18} /> {loading ? "Submitting..." : "Submit complaint"}
              </button>
            </form>
          </section>
        </section>
      </div>
    </main>
  );
}
