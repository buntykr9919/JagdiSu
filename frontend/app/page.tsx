"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft,
  BarChart3,
  BookOpen,
  BrainCircuit,
  Check,
  ChevronDown,
  ClipboardList,
  CreditCard,
  Crown,
  Download,
  EyeOff,
  FileCheck,
  GraduationCap,
  Lightbulb,
  LockKeyhole,
  LogOut,
  Mail,
  NotebookPen,
  Phone,
  RotateCcw,
  ScanText,
  Sparkles,
  StickyNote,
  Target,
  TrendingUp,
  Upload,
  UserRound,
  UsersRound,
  X
} from "lucide-react";
import {
  evaluateHandwrittenNotes,
  generateQuizWithBackend,
  generateTopicNotes,
  subscribeToPlan,
  type NotesEvaluationResponse
} from "../lib/backendQuizAi";

const FREE_DAILY_LIMIT = 10;
const QUIZ_BATCH_SIZE = 5;
const MAX_QUIZ_QUESTIONS = 150;
const ATTEMPTS_STORAGE_KEY = "jdsu-attempt-history";
const UPI_ID = "buntyaxis85@ybl";
const UPI_QR_IMAGE = "/upi-qr.jpeg";
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

type QuizRequest = {
  subject: string;
  examName: string;
  chapter?: string;
  language: string;
  numberOfQuestions: number;
  difficultyLevel: string;
  hintsEnabled: boolean;
  negativeMarking: number;
  totalQuestions?: number;
  batchNumber?: number;
  previousQuestionSummaries?: string[];
};

type Question = {
  id: string;
  question: string;
  options: string[];
  correctAnswerIndex: number;
  explanation: string;
  difficulty: string;
};

type QuizResponse = {
  quizId: string;
  examPatternSummary: string;
  difficulty: string;
  questions: Question[];
};

type Plan = "FREE" | "PRO" | "ADVANCED";
type ActiveView = "home" | "quiz" | "topic" | "notes" | "subscription" | "progress";
type PaidPlan = Extract<Plan, "PRO" | "ADVANCED">;

type Attempt = {
  id: string;
  date: string;
  subject: string;
  examName: string;
  chapter: string;
  score: number;
  total: number;
  percentage: number;
  correct: number;
  wrong: number;
  unattempted: number;
};

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

export default function HomePage() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [activeView, setActiveView] = useState<ActiveView>("home");
  const [plan, setPlan] = useState<Plan>("FREE");
  const [dailyUsage, setDailyUsage] = useState(0);
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [savedResultQuizId, setSavedResultQuizId] = useState("");
  const [subscriptionLoading, setSubscriptionLoading] = useState(false);
  const [subscriptionNotice, setSubscriptionNotice] = useState("");
  const [selectedSubscriptionPlan, setSelectedSubscriptionPlan] = useState<Plan>("FREE");
  const [paymentReference, setPaymentReference] = useState("");
  const [upiPaymentStarted, setUpiPaymentStarted] = useState(false);
  const [qrExpanded, setQrExpanded] = useState(false);
  const [form, setForm] = useState<QuizRequest>({
    subject: "Physics",
    examName: "JEE Main",
    chapter: "Modern Physics",
    language: "English",
    numberOfQuestions: 5,
    difficultyLevel: "Exam Pattern",
    hintsEnabled: true,
    negativeMarking: 0.25
  });
  const [negativeMode, setNegativeMode] = useState("0.25");
  const [quiz, setQuiz] = useState<QuizResponse | null>(null);
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [currentIndex, setCurrentIndex] = useState(0);
  const [targetQuestionCount, setTargetQuestionCount] = useState(0);
  const [batchLoading, setBatchLoading] = useState(false);
  const [prefetchNotice, setPrefetchNotice] = useState("");
  const [showResult, setShowResult] = useState(false);
  const [showHint, setShowHint] = useState(false);
  const [studentNotes, setStudentNotes] = useState("");
  const [topicForm, setTopicForm] = useState({ topic: "", language: "English" });
  const [topicNotes, setTopicNotes] = useState("");
  const [topicLoading, setTopicLoading] = useState(false);
  const [notesForm, setNotesForm] = useState({
    examName: "UPSC CSE",
    questionPaperText: "",
    answerText: "",
    language: "English"
  });
  const [notesFile, setNotesFile] = useState<File | null>(null);
  const [notesResult, setNotesResult] = useState<NotesEvaluationResponse | null>(null);
  const [notesLoading, setNotesLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [authIdentifier, setAuthIdentifier] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [signupMode, setSignupMode] = useState(false);
  const [signupUsername, setSignupUsername] = useState("");
  const [signupEmail, setSignupEmail] = useState("");
  const [signupMobile, setSignupMobile] = useState("");
  const [signupPassword, setSignupPassword] = useState("");
  const [signupConfirmPassword, setSignupConfirmPassword] = useState("");
  const [signupRole, setSignupRole] = useState("");
  const [signupTermsAccepted, setSignupTermsAccepted] = useState(false);
  const prefetchInFlightRef = useRef(false);
  const googleButtonRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const savedUser = window.localStorage.getItem("jdsu-user");
    const savedPlan = window.localStorage.getItem("jdsu-plan");
    const savedDate = window.localStorage.getItem("jdsu-usage-date");
    const savedUsage = Number(window.localStorage.getItem("jdsu-daily-usage") ?? "0");

    if (savedUser) {
      setIsLoggedIn(true);
    }
    setPlan(savedPlan === "ADVANCED" ? "ADVANCED" : savedPlan === "PRO" ? "PRO" : "FREE");
    setSelectedSubscriptionPlan(savedPlan === "ADVANCED" ? "ADVANCED" : savedPlan === "PRO" ? "PRO" : "FREE");
    if (savedDate === todayKey()) {
      setDailyUsage(Number.isFinite(savedUsage) ? savedUsage : 0);
    } else {
      window.localStorage.setItem("jdsu-usage-date", todayKey());
      window.localStorage.setItem("jdsu-daily-usage", "0");
      setDailyUsage(0);
    }

    try {
      const savedAttempts = JSON.parse(window.localStorage.getItem(ATTEMPTS_STORAGE_KEY) ?? "[]") as Attempt[];
      setAttempts(Array.isArray(savedAttempts) ? savedAttempts.slice(0, 20) : []);
    } catch {
      setAttempts([]);
    }
  }, []);

  useEffect(() => {
    if (!upiPaymentStarted) {
      return;
    }

    function handleReturnFromUpiApp() {
      setSubscriptionNotice(
        "Returned from UPI app. Direct UPI links do not send payment status to the website, so enter the UPI reference ID and confirm to activate."
      );
    }

    window.addEventListener("focus", handleReturnFromUpiApp);
    document.addEventListener("visibilitychange", handleReturnFromUpiApp);

    return () => {
      window.removeEventListener("focus", handleReturnFromUpiApp);
      document.removeEventListener("visibilitychange", handleReturnFromUpiApp);
    };
  }, [upiPaymentStarted]);

  useEffect(() => {
    if (isLoggedIn || !googleButtonRef.current) {
      return;
    }

    if (!GOOGLE_CLIENT_ID) {
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
          void loginWithGoogle(response.credential);
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
  }, [isLoggedIn]);

  const remainingFreeQuizzes = Math.max(0, FREE_DAILY_LIMIT - dailyUsage);
  const isPro = plan === "PRO" || plan === "ADVANCED";

  const score = useMemo(() => {
    if (!quiz) return 0;
    return quiz.questions.reduce((total, question) => {
      return total + (answers[question.id] === question.correctAnswerIndex ? 1 : 0);
    }, 0);
  }, [answers, quiz]);

  const result = useMemo(() => {
    if (!quiz) {
      return { correct: 0, wrong: 0, unattempted: 0, finalScore: 0 };
    }

    let correct = 0;
    let wrong = 0;
    let unattempted = 0;

    quiz.questions.forEach((question) => {
      const answer = answers[question.id];
      if (answer === undefined) {
        unattempted += 1;
      } else if (answer === question.correctAnswerIndex) {
        correct += 1;
      } else {
        wrong += 1;
      }
    });

    return {
      correct,
      wrong,
      unattempted,
      finalScore: Number((correct - wrong * form.negativeMarking).toFixed(2))
    };
  }, [answers, form.negativeMarking, quiz]);

  const progressStats = useMemo(() => {
    const latest = attempts[0];
    const previous = attempts[1];
    const best = attempts.reduce((top, attempt) => Math.max(top, attempt.percentage), 0);
    const average =
      attempts.length === 0
        ? 0
        : Math.round(attempts.reduce((total, attempt) => total + attempt.percentage, 0) / attempts.length);
    const improvement = latest && previous ? latest.percentage - previous.percentage : 0;
    const weakSubject = attempts
      .slice(0, 8)
      .reduce<Record<string, { total: number; count: number }>>((groups, attempt) => {
        const current = groups[attempt.subject] ?? { total: 0, count: 0 };
        groups[attempt.subject] = { total: current.total + attempt.percentage, count: current.count + 1 };
        return groups;
      }, {});
    const weakest = Object.entries(weakSubject)
      .map(([subject, value]) => ({ subject, average: value.total / value.count }))
      .sort((left, right) => left.average - right.average)[0];

    return { latest, previous, best, average, improvement, weakest };
  }, [attempts]);

  const coachingAdvice = useMemo(() => {
    if (attempts.length === 0) {
      return "Generate and submit a quiz to unlock personal recommendations.";
    }
    if (progressStats.latest && progressStats.latest.unattempted > progressStats.latest.total * 0.25) {
      return "Attempt more questions before submitting. Your next goal is reducing skipped questions.";
    }
    if (progressStats.weakest && progressStats.weakest.average < 60) {
      return `Prioritize ${progressStats.weakest.subject}. Revisit fundamentals, then take a short targeted quiz.`;
    }
    if (progressStats.improvement < 0) {
      return "Your last score dipped. Review wrong answers first, then repeat the same chapter with fewer questions.";
    }
    return "You are trending well. Increase difficulty gradually and keep reviewing explanations after each test.";
  }, [attempts, progressStats]);

  async function login() {
    setError("");
    setLoading(true);
    try {
      if (signupMode) {
        if (signupPassword !== signupConfirmPassword) {
          throw new Error("Password and confirm password do not match.");
        }
        if (!signupRole) {
          throw new Error("Please select your role.");
        }
        if (!signupTermsAccepted) {
          throw new Error("Please accept the terms and conditions.");
        }

        const response = await fetch("http://localhost:2000/api/auth/signup", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: signupUsername,
            email: signupEmail,
            password: signupPassword
          })
        });

        if (!response.ok) {
          const body = await response.json().catch(() => ({}));
          throw new Error(body.error || body.message || "Signup failed");
        }

        const user = await response.json();
        saveLoggedInUser(user);
        return;
      }

      const response = await fetch("http://localhost:2000/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          usernameOrEmail: authIdentifier,
          password: authPassword
        })
      });

      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.error || body.message || "Login failed");
      }

      const user = await response.json();
      saveLoggedInUser(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }

  function saveLoggedInUser(user: { plan?: string }) {
    window.localStorage.setItem("jdsu-user", JSON.stringify(user));
    window.localStorage.setItem("jdsu-plan", user.plan ?? "FREE");
    setPlan(user.plan === "ADVANCED" ? "ADVANCED" : user.plan === "PRO" ? "PRO" : "FREE");
    setIsLoggedIn(true);
    setActiveView("home");
  }

  async function loginWithGoogle(credential?: string) {
    setError("");
    if (!GOOGLE_CLIENT_ID) {
      setError("Google login setup missing hai. frontend/.env.local me NEXT_PUBLIC_GOOGLE_CLIENT_ID add karo.");
      return;
    }
    if (!credential) {
      setError("Google login credential nahi mila. Please try again.");
      return;
    }

    setLoading(true);
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
      saveLoggedInUser(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Google login failed.");
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    window.localStorage.removeItem("jdsu-user");
    window.localStorage.removeItem("jdsu-plan");
    setIsLoggedIn(false);
    setActiveView("home");
    setQuiz(null);
    setAnswers({});
    setCurrentIndex(0);
    setShowResult(false);
    setPlan("FREE");
  }

  async function submitTopicNotes() {
    if (!topicForm.topic.trim() || topicLoading) {
      return;
    }

    setError("");
    setTopicLoading(true);
    setTopicNotes("");
    try {
      const response = await generateTopicNotes({ topic: topicForm.topic.trim(), language: topicForm.language });
      setTopicNotes(response.notes);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Topic notes could not be generated.");
    } finally {
      setTopicLoading(false);
    }
  }

  async function submitNotesEvaluation() {
    if (notesLoading || !notesForm.examName.trim() || (!notesFile && !notesForm.answerText.trim())) {
      return;
    }

    setError("");
    setNotesLoading(true);
    setNotesResult(null);
    try {
      const response = await evaluateHandwrittenNotes({
        ...notesForm,
        examName: notesForm.examName.trim(),
        file: notesFile
      });
      setNotesResult(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Handwritten notes could not be checked.");
    } finally {
      setNotesLoading(false);
    }
  }

  function normalizeQuestionCount(value: number) {
    if (!Number.isFinite(value)) {
      return QUIZ_BATCH_SIZE;
    }
    return Math.min(MAX_QUIZ_QUESTIONS, Math.max(1, Math.round(value)));
  }

  function summarizePreviousQuestions(questions: Question[]) {
    return questions
      .slice(-25)
      .map((question, index) => `${index + 1}. ${question.question.slice(0, 220)}`);
  }

  function appendUniqueQuestions(existing: Question[], incoming: Question[]) {
    const seen = new Set(existing.map((question) => question.question.trim().toLowerCase()));
    const next = [...existing];
    incoming.forEach((question) => {
      const key = question.question.trim().toLowerCase();
      if (!seen.has(key)) {
        seen.add(key);
        next.push({
          ...question,
          id: `${question.id}-${next.length + 1}`
        });
      }
    });
    return next;
  }

  async function loadNextQuestionBatch(currentQuiz: QuizResponse, totalQuestions: number) {
    if (prefetchInFlightRef.current || currentQuiz.questions.length >= totalQuestions) {
      return;
    }

    const remaining = totalQuestions - currentQuiz.questions.length;
    const batchSize = Math.min(QUIZ_BATCH_SIZE, remaining);
    const batchNumber = Math.floor(currentQuiz.questions.length / QUIZ_BATCH_SIZE) + 1;

    prefetchInFlightRef.current = true;
    setBatchLoading(true);
    setPrefetchNotice("");

    try {
      const generatedBatch = await generateQuizWithBackend({
        ...form,
        numberOfQuestions: batchSize,
        totalQuestions,
        batchNumber,
        previousQuestionSummaries: summarizePreviousQuestions(currentQuiz.questions)
      });

      setQuiz((existingQuiz) => {
        if (!existingQuiz) {
          return existingQuiz;
        }
        const mergedQuestions = appendUniqueQuestions(existingQuiz.questions, generatedBatch.questions);
        return {
          ...existingQuiz,
          questions: mergedQuestions.slice(0, totalQuestions)
        };
      });
      setPrefetchNotice("");
    } catch (err) {
      setPrefetchNotice(err instanceof Error ? err.message : "The next question set could not be loaded yet.");
    } finally {
      prefetchInFlightRef.current = false;
      setBatchLoading(false);
    }
  }

  useEffect(() => {
    if (!quiz || showResult || targetQuestionCount <= quiz.questions.length) {
      return;
    }

    const loadedAhead = quiz.questions.length - currentIndex - 1;
    if (loadedAhead <= QUIZ_BATCH_SIZE) {
      void loadNextQuestionBatch(quiz, targetQuestionCount);
    }
  }, [currentIndex, quiz, showResult, targetQuestionCount]);

  async function generateQuiz() {
    if (loading) {
      return;
    }

    setError("");

    if (!isPro && dailyUsage >= FREE_DAILY_LIMIT) {
      setActiveView("subscription");
      setSubscriptionNotice("You have used all 10 free quizzes for today. Subscribe to Pro to continue.");
      return;
    }

    const totalQuestions = normalizeQuestionCount(form.numberOfQuestions);
    const firstBatchSize = Math.min(QUIZ_BATCH_SIZE, totalQuestions);

    setLoading(true);
    setBatchLoading(false);
    setTargetQuestionCount(totalQuestions);
    setPrefetchNotice("");
    prefetchInFlightRef.current = false;
    setAnswers({});
    setCurrentIndex(0);
    setShowResult(false);
    setShowHint(false);
    setStudentNotes("");
    setSavedResultQuizId("");
    try {
      const generatedQuiz = await generateQuizWithBackend({
        ...form,
        numberOfQuestions: firstBatchSize,
        totalQuestions,
        batchNumber: 1,
        previousQuestionSummaries: []
      });
      setQuiz(generatedQuiz);
      if (!isPro) {
        const nextUsage = dailyUsage + 1;
        setDailyUsage(nextUsage);
        window.localStorage.setItem("jdsu-usage-date", todayKey());
        window.localStorage.setItem("jdsu-daily-usage", String(nextUsage));
      }
    } catch (err) {
      setQuiz(null);
      setError(err instanceof Error ? err.message : "The AI quiz could not be generated.");
    } finally {
      setLoading(false);
    }
  }

  function completeQuiz() {
    if (!quiz || savedResultQuizId === quiz.quizId) {
      setShowResult(true);
      return;
    }

    const percentage = Math.max(0, Math.round((result.finalScore / quiz.questions.length) * 100));
    const attempt: Attempt = {
      id: `${quiz.quizId}-${Date.now()}`,
      date: new Date().toISOString(),
      subject: form.subject,
      examName: form.examName,
      chapter: form.chapter?.trim() || "All chapters",
      score: result.finalScore,
      total: quiz.questions.length,
      percentage,
      correct: result.correct,
      wrong: result.wrong,
      unattempted: result.unattempted
    };
    const nextAttempts = [attempt, ...attempts].slice(0, 20);
    setAttempts(nextAttempts);
    setSavedResultQuizId(quiz.quizId);
    window.localStorage.setItem(ATTEMPTS_STORAGE_KEY, JSON.stringify(nextAttempts));
    setShowResult(true);
  }

  function downloadNotes() {
    const lines = [
      "JagdiSu Quiz Notes",
      `Exam: ${form.examName}`,
      `Subject: ${form.subject}`,
      `Chapter: ${form.chapter?.trim() || "All chapters"}`,
      `Difficulty: ${quiz?.difficulty ?? form.difficultyLevel}`,
      `Score: ${result.finalScore}/${quiz?.questions.length ?? 0}`,
      "",
      studentNotes.trim() || "No notes written."
    ];
    const blob = createNotesPdf(lines);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `jagdisu-notes-${Date.now()}.pdf`;
    link.click();
    URL.revokeObjectURL(url);
  }

  function createNotesPdf(lines: string[]) {
    const wrappedLines = lines.flatMap((line) => wrapPdfLine(line, 86));
    const pages: string[][] = [];
    for (let index = 0; index < wrappedLines.length; index += 42) {
      pages.push(wrappedLines.slice(index, index + 42));
    }

    const pageRefs = pages.map((_, index) => 4 + index * 2);
    const objects: string[] = [
      `<< /Type /Pages /Kids [${pageRefs.map((ref) => `${ref} 0 R`).join(" ")}] /Count ${pages.length} >>`,
      "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    ];

    pages.forEach((pageLines, pageIndex) => {
      const content = [
        "BT",
        "/F1 12 Tf",
        "50 790 Td",
        "16 TL",
        ...pageLines.map((line, index) => `${index === 0 ? "" : "T*"}(${escapePdfText(line)}) Tj`),
        "ET"
      ].join("\n");
      const contentObjectNumber = 3 + pageIndex * 2;
      const pageObjectNumber = 4 + pageIndex * 2;
      objects.push(`<< /Length ${content.length} >>\nstream\n${content}\nendstream`);
      objects.push(`<< /Type /Page /Parent 1 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 2 0 R >> >> /Contents ${contentObjectNumber} 0 R >>`);
    });

    objects.push("<< /Type /Catalog /Pages 1 0 R >>");

    let pdf = "%PDF-1.4\n";
    const offsets: number[] = [0];
    objects.forEach((object, index) => {
      offsets.push(pdf.length);
      pdf += `${index + 1} 0 obj\n${object}\nendobj\n`;
    });
    const xrefOffset = pdf.length;
    pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
    offsets.slice(1).forEach((offset) => {
      pdf += `${String(offset).padStart(10, "0")} 00000 n \n`;
    });
    pdf += `trailer\n<< /Size ${objects.length + 1} /Root ${objects.length} 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`;
    return new Blob([pdf], { type: "application/pdf" });
  }

  function downloadTopicNotes() {
    const lines = [
      "JagdiSu TM Topic Notes",
      `Topic: ${topicForm.topic.trim() || "Topic"}`,
      `Language: ${topicForm.language}`,
      "Document trademark: JagdiSu TM",
      "",
      stripHighlightMarks(topicNotes || "No notes generated.")
    ];
    const blob = createNotesPdf(lines);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `jagdisu-topic-notes-${Date.now()}.pdf`;
    link.click();
    URL.revokeObjectURL(url);
  }

  function wrapPdfLine(line: string, maxLength: number) {
    if (!line) {
      return [""];
    }
    const words = line.split(/\s+/);
    const output: string[] = [];
    let current = "";
    words.forEach((word) => {
      const next = current ? `${current} ${word}` : word;
      if (next.length > maxLength && current) {
        output.push(current);
        current = word;
      } else {
        current = next;
      }
    });
    output.push(current);
    return output;
  }

  function escapePdfText(text: string) {
    return text
      .replace(/[^\x20-\x7E]/g, "?")
      .replace(/\\/g, "\\\\")
      .replace(/\(/g, "\\(")
      .replace(/\)/g, "\\)");
  }

  function stripHighlightMarks(value: string) {
    return value.replace(/\*\*/g, "");
  }

  function renderHighlightedText(value: string) {
    return value.split(/(\*\*[^*]+\*\*)/g).map((part, index) => {
      if (part.startsWith("**") && part.endsWith("**")) {
        return <mark key={`${part}-${index}`}>{part.slice(2, -2)}</mark>;
      }
      return <span key={`${part}-${index}`}>{part}</span>;
    });
  }

  function goBack() {
    setError("");
    if (activeView === "quiz" && showResult) {
      setShowResult(false);
      setCurrentIndex(0);
      setShowHint(false);
      return;
    }
    if (activeView === "quiz" && quiz) {
      setQuiz(null);
      setShowResult(false);
      setShowHint(false);
      setTargetQuestionCount(0);
      setPrefetchNotice("");
      return;
    }
    if (activeView !== "home") {
      setActiveView("home");
    }
  }

  const selectedPaidPlan = selectedSubscriptionPlan === "ADVANCED" ? "ADVANCED" : "PRO";
  const selectedPlanPrice = selectedSubscriptionPlan === "ADVANCED" ? 49 : 20;
  const upiPaymentUrl = `upi://pay?pa=${encodeURIComponent(UPI_ID)}&pn=${encodeURIComponent("JagdiSu")}&am=${selectedPlanPrice}&cu=INR&tn=${encodeURIComponent(`${selectedPaidPlan} subscription`)}`;

  async function subscribe(nextPlan: PaidPlan) {
    if (!paymentReference.trim()) {
      setSubscriptionNotice("Payment ke baad UPI reference/transaction ID enter karo, phir plan activate hoga.");
      return;
    }

    setSubscriptionLoading(true);
    setError("");
    setSubscriptionNotice("");

    try {
      await subscribeToPlan(nextPlan, paymentReference.trim() || "UPI_MANUAL_CONFIRMATION");
      setPlan(nextPlan);
      window.localStorage.setItem("jdsu-plan", nextPlan);
      setSubscriptionNotice(`Your ${nextPlan === "ADVANCED" ? "Advanced" : "Pro"} plan is active.`);
      setActiveView("quiz");
      setPaymentReference("");
    } catch (err) {
      setSubscriptionNotice(err instanceof Error ? err.message : "The subscription could not be activated.");
    } finally {
      setSubscriptionLoading(false);
    }
  }

  if (!isLoggedIn) {
    return (
      <main className="login-shell">
        <section className="login-hero">
          <div className="login-copy">
            <div className="brand">
              <span className="brand-mark">
                <GraduationCap size={25} />
              </span>
              JagdiSu
            </div>
            <h1 className="login-title">Welcome Back!</h1>
            <span className="title-rule" />
            <p className="login-subtitle">Continue your learning journey with JagdiSu.</p>
            <div className="login-metrics">
              <span>
                <strong>Free</strong>
                Plan
              </span>
              <span>
                <strong>Smart</strong>
                Quiz
              </span>
              <span>
                <strong>JEE</strong>
                NEET/UPSC
              </span>
            </div>
          </div>

          <div className="auth-wrap panel panel-pad login-card">
            <span className="login-card-logo">
              <GraduationCap size={42} />
              <BookOpen size={32} />
            </span>
            <h2 className="auth-title">JagdiSu</h2>
            <p className="subtitle">Login to your account</p>
            <span className="title-rule compact" />

            <div className="form-grid" style={{ gridTemplateColumns: "1fr" }}>
              <label className="field">
                <div className="input-icon">
                  <UserRound size={24} />
                  <input
                    className="input bare"
                    placeholder="Username or Email"
                    value={authIdentifier}
                    onChange={(event) => setAuthIdentifier(event.target.value)}
                  />
                </div>
              </label>
              <label className="field">
                <div className="input-icon">
                  <LockKeyhole size={23} />
                  <input
                    className="input bare"
                    type="password"
                    placeholder="Password"
                    value={authPassword}
                    onChange={(event) => setAuthPassword(event.target.value)}
                  />
                  <EyeOff size={22} />
                </div>
              </label>
              <div className="login-row">
                <label className="remember">
                  <input type="checkbox" />
                  Remember me
                </label>
                <button className="text-link" type="button">Forgot Password?</button>
              </div>
              {error ? <div className="error">{error}</div> : null}
              <button className="button primary" onClick={login} disabled={loading} type="button">
                <GraduationCap size={23} /> {loading ? "Please wait..." : "Login"}
              </button>
              <button
                className="button ghost"
                onClick={() => {
                  window.location.href = "/signup";
                  setError("");
                }}
                disabled={loading}
                type="button"
              >
                <UserRound size={22} /> Signup
              </button>
              <div className="divider"><span>or</span></div>
              {GOOGLE_CLIENT_ID ? (
                <div className="google-login-slot" ref={googleButtonRef} />
              ) : (
                <button className="button google" onClick={() => loginWithGoogle()} disabled={loading} type="button">
                  <span className="google-mark">G</span> Login with Google
                </button>
              )}
              <p className="quote">Learn Today, Achieve Tomorrow.</p>
            </div>
          </div>
        </section>
        {signupMode ? (
          <div className="auth-modal-backdrop" role="presentation" onClick={() => setSignupMode(false)}>
            <section
              className="auth-modal signup-showcase"
              role="dialog"
              aria-modal="true"
              aria-labelledby="signup-title"
              onClick={(event) => event.stopPropagation()}
            >
              <button
                className="modal-close"
                type="button"
                aria-label="Close signup"
                onClick={() => {
                  setSignupMode(false);
                  setError("");
                }}
              >
                <X size={20} />
              </button>
              <div className="signup-info">
                <div className="signup-brand">
                  <span className="signup-brand-mark">
                    <GraduationCap size={42} />
                  </span>
                  <strong>Jagdi<span>Su</span></strong>
                </div>
                <div>
                  <h2>Learn Smart.<br />Grow Faster.</h2>
                  <p>JagdiSu is your AI-powered learning platform to achieve more every day.</p>
                </div>
                <div className="signup-benefits">
                  <div className="signup-benefit">
                    <span><BookOpen size={24} /></span>
                    <div>
                      <strong>Free Courses</strong>
                      <p>Access quality content absolutely free.</p>
                    </div>
                  </div>
                  <div className="signup-benefit">
                    <span><BrainCircuit size={24} /></span>
                    <div>
                      <strong>AI Learning</strong>
                      <p>Smart recommendations just for you.</p>
                    </div>
                  </div>
                  <div className="signup-benefit">
                    <span><BarChart3 size={24} /></span>
                    <div>
                      <strong>Progress Tracking</strong>
                      <p>Track your progress and achieve your goals.</p>
                    </div>
                  </div>
                </div>
                <div className="learner-strip">
                  <span>A</span>
                  <span>J</span>
                  <span>S</span>
                  <strong>Join 50K+ learners</strong>
                  <p>growing every day!</p>
                </div>
                <div className="student-visual" aria-hidden="true">
                  <span className="student-head" />
                  <span className="student-body" />
                  <span className="student-laptop"><GraduationCap size={22} /></span>
                  <span className="floating-cap"><GraduationCap size={54} /></span>
                  <span className="floating-ai">AI</span>
                  <span className="floating-chart"><BarChart3 size={34} /></span>
                </div>
              </div>
              <div className="signup-form-panel">
                <div className="theme-toggle" aria-hidden="true">
                  <span><Sparkles size={15} /></span>
                  <span />
                </div>
                <h2 id="signup-title" className="modal-title">Create Your Account</h2>
                <p className="signup-panel-subtitle">Join JagdiSu and start your learning journey today.</p>
                <div className="signup-form">
                  <label className="signup-input">
                    <UserRound size={22} />
                    <input
                      placeholder="Full Name"
                      value={signupUsername}
                      onChange={(event) => setSignupUsername(event.target.value)}
                    />
                  </label>
                  <label className="signup-input">
                    <Mail size={22} />
                    <input
                      type="email"
                      placeholder="Email Address"
                      value={signupEmail}
                      onChange={(event) => setSignupEmail(event.target.value)}
                    />
                  </label>
                  <div className="signup-phone-row">
                    <button className="country-code" type="button">
                      +91 <ChevronDown size={17} />
                    </button>
                    <label className="signup-input">
                      <Phone size={22} />
                      <input
                        inputMode="tel"
                        placeholder="Mobile Number"
                        value={signupMobile}
                        onChange={(event) => setSignupMobile(event.target.value)}
                      />
                    </label>
                  </div>
                  <label className="signup-input">
                    <LockKeyhole size={22} />
                    <input
                      type="password"
                      placeholder="Password"
                      value={signupPassword}
                      onChange={(event) => setSignupPassword(event.target.value)}
                    />
                    <EyeOff size={21} />
                  </label>
                  <label className="signup-input">
                    <LockKeyhole size={22} />
                    <input
                      type="password"
                      placeholder="Confirm Password"
                      value={signupConfirmPassword}
                      onChange={(event) => setSignupConfirmPassword(event.target.value)}
                    />
                    <EyeOff size={21} />
                  </label>
                  <label className="signup-input select-input">
                    <UsersRound size={22} />
                    <select value={signupRole} onChange={(event) => setSignupRole(event.target.value)}>
                      <option value="">Select Your Role</option>
                      <option value="Student">Student</option>
                      <option value="Teacher">Teacher</option>
                      <option value="Parent">Parent</option>
                    </select>
                    <ChevronDown size={19} />
                  </label>
                  <label className="terms-row">
                    <input
                      type="checkbox"
                      checked={signupTermsAccepted}
                      onChange={(event) => setSignupTermsAccepted(event.target.checked)}
                    />
                    <span className="terms-check"><Check size={15} /></span>
                    <span>I agree to the <button type="button">Terms & Conditions</button></span>
                  </label>
                  {error ? <div className="error signup-error">{error}</div> : null}
                  <button className="signup-submit" onClick={login} disabled={loading} type="button">
                    {loading ? "Please wait..." : "Sign Up"}
                  </button>
                  <p className="login-switch">
                    Already have an account?{" "}
                    <button
                      type="button"
                      onClick={() => {
                        setSignupMode(false);
                        setError("");
                      }}
                    >
                      Login
                    </button>
                  </p>
                </div>
              </div>
            </section>
          </div>
        ) : null}
      </main>
    );
  }

  const question = quiz?.questions[currentIndex];
  const totalQuizQuestions = targetQuestionCount || quiz?.questions.length || form.numberOfQuestions;
  const progress = quiz ? ((currentIndex + 1) / totalQuizQuestions) * 100 : 0;
  const isTakingQuiz = activeView === "quiz" && Boolean(quiz) && !showResult;

  return (
    <main className="app-shell">
      <section className="screen">
        <header className="topbar">
          <div className="brand">
            <span className="brand-mark">
              <GraduationCap size={24} />
            </span>
            JagdiSu
          </div>
          <div className="nav-actions">
            {(activeView !== "home" || Boolean(quiz) || showResult) ? (
              <button className="button ghost back-button" onClick={goBack} title="Back" type="button">
                <ArrowLeft size={18} /> Back
              </button>
            ) : null}
            <button className="nav-tab" onClick={() => setActiveView("quiz")} type="button">
              <Sparkles size={16} /> Quiz
            </button>
            <button className="nav-tab" onClick={() => setActiveView("topic")} type="button">
              <BookOpen size={16} /> Study
            </button>
            <button className="nav-tab" onClick={() => setActiveView("notes")} type="button">
              <NotebookPen size={16} /> Notes
            </button>
            <button className="nav-tab" onClick={() => setActiveView("progress")} type="button">
              <BarChart3 size={16} /> Progress
            </button>
            <button className="button ghost" onClick={() => setActiveView("subscription")} title="Subscription" type="button">
              <Crown size={18} />
            </button>
            <button className="button ghost" onClick={logout} title="Logout" type="button">
              <LogOut size={18} /> Logout
            </button>
          </div>
        </header>

        <div className={`layout ${isTakingQuiz ? "" : "solo"}`}>
          <section className="panel panel-pad">
            {activeView === "home" ? (
              <div className="choice-page">
                <span className="pill">
                  <GraduationCap size={15} /> Start Learning
                </span>
                <h1 className="section-title">Choose what to do</h1>
                <p className="subtitle">Take a test, read AI notes, or check handwritten answers.</p>
                <div className="choice-grid">
                  <button className="choice-card" type="button" onClick={() => setActiveView("quiz")}>
                    <span className="choice-icon"><Sparkles size={26} /></span>
                    <span className="plan-kicker">Assessment</span>
                    <strong>Start a Test</strong>
                    <span>Create an exam-style quiz with difficulty, language, notes, hints, and result tracking.</span>
                    <em>Practice now</em>
                  </button>
                  <button className="choice-card" type="button" onClick={() => setActiveView("topic")}>
                    <span className="choice-icon"><BookOpen size={26} /></span>
                    <span className="plan-kicker">Study Mode</span>
                    <strong>Study a Topic</strong>
                    <span>Generate complete AI notes in English, Hindi, or Hinglish with highlighted key points.</span>
                    <em>Prepare first</em>
                  </button>
                  <button className="choice-card" type="button" onClick={() => setActiveView("notes")}>
                    <span className="choice-icon"><ScanText size={26} /></span>
                    <span className="plan-kicker">OCR Checker</span>
                    <strong>Check Handwritten Notes</strong>
                    <span>Upload answers, add the target exam, and get OCR text, mistakes, and exam-style marks.</span>
                    <em>Scan answer</em>
                  </button>
                </div>
              </div>
            ) : activeView === "topic" ? (
              <div className="topic-page">
                <span className="pill">
                  <BookOpen size={15} /> Topic Notes
                </span>
                <h1 className="section-title">Study Topic</h1>
                <p className="subtitle">Topic ka naam likho aur language select karo. JagdiSu AI complete notes generate karega.</p>
                <div className="topic-form">
                  <label className="field topic-field">
                    <span className="label-row">Topic</span>
                    <textarea
                      className="topic-input"
                      placeholder="e.g., Photosynthesis, Indian Constitution, Integration"
                      value={topicForm.topic}
                      onChange={(event) => setTopicForm({ ...topicForm, topic: event.target.value })}
                      rows={2}
                      style={{ height: `${Math.max(76, 52 + topicForm.topic.split("\n").length * 28 + Math.floor(topicForm.topic.length / 72) * 24)}px` }}
                    />
                  </label>
                  <div className="topic-action-panel">
                    <label className="field">
                      <span className="label-row">Language</span>
                      <select
                        className="select"
                        value={topicForm.language}
                        onChange={(event) => setTopicForm({ ...topicForm, language: event.target.value })}
                      >
                        <option>English</option>
                        <option>Hindi</option>
                        <option>Hinglish</option>
                      </select>
                    </label>
                    <button className="button primary" disabled={topicLoading || !topicForm.topic.trim()} onClick={submitTopicNotes}>
                      <Sparkles size={18} /> {topicLoading ? "Generating Notes..." : "Generate Notes"}
                    </button>
                    <p>UPSC CSE depth with highlighted points and memory tricks.</p>
                  </div>
                  {error ? <div className="error topic-error">{error}</div> : null}
                </div>
                {topicNotes ? (
                  <section className="topic-notes">
                    <div className="notes-head">
                      <h2>JagdiSu Topic Notes</h2>
                      <button className="button ghost" type="button" onClick={downloadTopicNotes}>
                        <Download size={18} /> Download PDF
                      </button>
                    </div>
                    <p className="trademark-line">JagdiSu TM study document</p>
                    <div className="notes-content">
                      {topicNotes.split("\n").map((line, index) => (
                        <p key={`${line}-${index}`}>{renderHighlightedText(line)}</p>
                      ))}
                    </div>
                  </section>
                ) : null}
              </div>
            ) : activeView === "notes" ? (
              <div className="notes-check-page">
                <span className="pill">
                  <ScanText size={15} /> Handwritten Notes OCR
                </span>
                <h1 className="section-title">Check Answer Notes</h1>
                <p className="subtitle">
                  Exam ka naam likho, handwritten answer upload karo, aur AI OCR se text, galtiyan, feedback, score dekho.
                </p>
                <div className="notes-check-grid">
                  <section className="upload-panel">
                    <label className="field">
                      <span className="label-row">Exam Name</span>
                      <input
                        className="input"
                        placeholder="e.g., UPSC CSE, NEET, JEE Main"
                        value={notesForm.examName}
                        onChange={(event) => setNotesForm({ ...notesForm, examName: event.target.value })}
                      />
                    </label>
                    <label className="field">
                      <span className="label-row">Language</span>
                      <select
                        className="select"
                        value={notesForm.language}
                        onChange={(event) => setNotesForm({ ...notesForm, language: event.target.value })}
                      >
                        <option>English</option>
                        <option>Hindi</option>
                        <option>Hinglish</option>
                      </select>
                    </label>
                    <label className="file-drop">
                      <Upload size={28} />
                      <strong>{notesFile ? notesFile.name : "Upload handwritten answer image"}</strong>
                      <span>PNG, JPG, or JPEG works best. Clear light and straight page helps OCR.</span>
                      <input
                        type="file"
                        accept="image/png,image/jpeg,image/jpg"
                        onChange={(event) => {
                          setNotesFile(event.target.files?.[0] ?? null);
                          setNotesResult(null);
                        }}
                      />
                    </label>
                    <label className="field">
                      <span className="label-row">Question / Paper Context</span>
                      <textarea
                        className="topic-input tall"
                        placeholder="Question yahan paste karo. Optional, but score zyada accurate hoga."
                        value={notesForm.questionPaperText}
                        onChange={(event) => setNotesForm({ ...notesForm, questionPaperText: event.target.value })}
                      />
                    </label>
                    <label className="field">
                      <span className="label-row">Typed Answer / OCR Correction</span>
                      <textarea
                        className="topic-input tall"
                        placeholder="Agar OCR unclear ho sakta hai to answer yahan type/paste kar do."
                        value={notesForm.answerText}
                        onChange={(event) => setNotesForm({ ...notesForm, answerText: event.target.value })}
                      />
                    </label>
                    {error ? <div className="error">{error}</div> : null}
                    <button
                      className="button primary"
                      disabled={notesLoading || !notesForm.examName.trim() || (!notesFile && !notesForm.answerText.trim())}
                      onClick={submitNotesEvaluation}
                    >
                      <FileCheck size={18} /> {notesLoading ? "Checking Notes..." : "Check Notes"}
                    </button>
                  </section>
                  <section className="checker-preview">
                    {notesLoading ? (
                      <div className="notes-ai-loader">
                        <BrainCircuit size={38} />
                        <div className="scan-sheet">
                          <span />
                          <span />
                          <span />
                        </div>
                        <strong>AI OCR page ko padh raha hai</strong>
                        <p>Teacher mode on: spelling, concept, aur marks sab check ho rahe hain.</p>
                      </div>
                    ) : notesResult ? (
                      <div className="evaluation-result">
                        <div className="score-ring">
                          <strong>{notesResult.score}</strong>
                          <span>/{notesResult.maxScore}</span>
                        </div>
                        <div>
                          <span className="plan-kicker">{notesResult.examName}</span>
                          <h2>Answer Feedback</h2>
                          <p>{notesResult.feedback}</p>
                        </div>
                        <div className="evaluation-block">
                          <h3>OCR Text</h3>
                          <p>{notesResult.extractedText || "No readable text returned."}</p>
                        </div>
                        <div className="evaluation-columns">
                          <div>
                            <h3>Mistakes</h3>
                            {notesResult.mistakes.map((item) => (
                              <p key={item}>{item}</p>
                            ))}
                          </div>
                          <div>
                            <h3>Strengths</h3>
                            {notesResult.strengths.map((item) => (
                              <p key={item}>{item}</p>
                            ))}
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="empty-state checker-empty">
                        <ScanText size={42} />
                        <strong>Upload and check</strong>
                        <span>Result me OCR text, mistakes, strengths, aur exam score yahin dikhega.</span>
                      </div>
                    )}
                  </section>
                </div>
              </div>
            ) : activeView === "progress" ? (
              <div className="progress-page">
                <span className="pill">
                  <TrendingUp size={15} /> Learning Dashboard
                </span>
                <h1 className="section-title">Your Progress</h1>
                <p className="subtitle">Track your scores, improvement, and where to focus next.</p>
                <div className="insight-grid">
                  <div className="insight-card">
                    <span>Latest Score</span>
                    <strong>{progressStats.latest ? `${progressStats.latest.percentage}%` : "--"}</strong>
                  </div>
                  <div className="insight-card">
                    <span>Average</span>
                    <strong>{attempts.length ? `${progressStats.average}%` : "--"}</strong>
                  </div>
                  <div className="insight-card">
                    <span>Best</span>
                    <strong>{attempts.length ? `${progressStats.best}%` : "--"}</strong>
                  </div>
                  <div className="insight-card">
                    <span>Change</span>
                    <strong>{attempts.length > 1 ? `${progressStats.improvement >= 0 ? "+" : ""}${progressStats.improvement}%` : "--"}</strong>
                  </div>
                </div>
                <section className="coach-panel">
                  <Target size={22} />
                  <div>
                    <h2>Recommended Focus</h2>
                    <p>{coachingAdvice}</p>
                  </div>
                </section>
                <div className="history-list">
                  {attempts.length === 0 ? (
                    <div className="empty-state">
                      <ClipboardList size={36} />
                      <strong>No attempts yet</strong>
                      <span>Your submitted quiz results will appear here.</span>
                    </div>
                  ) : (
                    attempts.map((attempt) => (
                      <div className="history-row" key={attempt.id}>
                        <div>
                          <strong>{attempt.examName} - {attempt.subject}</strong>
                          <span>{attempt.chapter} | {new Date(attempt.date).toLocaleDateString()}</span>
                        </div>
                        <div className="history-score">
                          <strong>{attempt.percentage}%</strong>
                          <span>{attempt.score}/{attempt.total}</span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            ) : activeView === "subscription" ? (
              <div className="subscription-page">
                <span className="pill">
                  <Crown size={15} /> Subscription
                </span>
                <h1 className="section-title">Choose Plan</h1>
                <p className="subtitle">
                  Pick the plan that matches how much practice and support you need.
                </p>
                {subscriptionNotice ? <div className="notice">{subscriptionNotice}</div> : null}
                <div className="plan-grid">
                  <button
                    className={`plan-box plan-select ${plan === "FREE" ? "active" : ""}`}
                    onClick={() => {
                      setPlan("FREE");
                      setSelectedSubscriptionPlan("FREE");
                      window.localStorage.setItem("jdsu-plan", "FREE");
                      setSubscriptionNotice("Free plan selected. Limited tests are active.");
                    }}
                    type="button"
                  >
                    <span className="plan-kicker">Limited</span>
                    <h2>Free</h2>
                    <strong>Rs 0</strong>
                    <p className="subtitle">Limited test generations for daily practice.</p>
                    <div className="usage-meter">
                      <span style={{ width: `${Math.min(100, (dailyUsage / FREE_DAILY_LIMIT) * 100)}%` }} />
                    </div>
                    <p className="subtitle">
                      {plan === "FREE" ? `${remainingFreeQuizzes} free quizzes left today.` : "Available anytime."}
                    </p>
                  </button>
                  <button
                    className={`plan-box plan-select featured ${selectedSubscriptionPlan === "PRO" ? "selected" : ""} ${plan === "PRO" ? "active" : ""}`}
                    onClick={() => setSelectedSubscriptionPlan("PRO")}
                    type="button"
                  >
                    <span className="plan-kicker">Unlimited</span>
                    <h2>Pro</h2>
                    <strong>Rs 20/month</strong>
                    <p className="subtitle">Unlimited test generations for regular preparation.</p>
                    <span className="plan-action">{plan === "PRO" ? "Active" : "Select Pro"}</span>
                  </button>
                  <button
                    className={`plan-box plan-select advanced ${selectedSubscriptionPlan === "ADVANCED" ? "selected" : ""} ${plan === "ADVANCED" ? "active" : ""}`}
                    onClick={() => setSelectedSubscriptionPlan("ADVANCED")}
                    type="button"
                  >
                    <span className="plan-kicker">Advanced</span>
                    <h2>Advanced</h2>
                    <strong>Rs 49/month</strong>
                    <p className="subtitle">Unlimited tests plus advanced features for deeper analysis and smarter practice.</p>
                    <span className="plan-action">{plan === "ADVANCED" ? "Active" : "Select Advanced"}</span>
                  </button>
                </div>
                {selectedSubscriptionPlan === "FREE" ? null : (
                <section className="upi-panel">
                  <div>
                    <span className="plan-kicker">UPI Payment</span>
                    <h2>{selectedPaidPlan === "ADVANCED" ? "Advanced" : "Pro"} - Rs {selectedPlanPrice}/month</h2>
                    <p className="subtitle">Pay using any UPI app, then enter the transaction/reference ID to activate the plan.</p>
                  </div>
                  <div className="upi-grid">
                    <button className="qr-button" type="button" onClick={() => setQrExpanded(true)}>
                      <img className="upi-qr-image" src={UPI_QR_IMAGE} alt={`UPI QR code for ${UPI_ID}`} />
                    </button>
                    <div className="upi-details">
                      <div className="stat">
                        <span>UPI ID</span>
                        <strong>{UPI_ID}</strong>
                      </div>
                      <a className="button secondary" href={upiPaymentUrl} onClick={() => setUpiPaymentStarted(true)}>
                        <CreditCard size={18} /> Pay with UPI App
                      </a>
                      <input
                        className="input"
                        placeholder="UPI transaction/reference ID"
                        value={paymentReference}
                        onChange={(event) => setPaymentReference(event.target.value)}
                      />
                      <button className="button primary" disabled={subscriptionLoading} onClick={() => subscribe(selectedPaidPlan)}>
                        <CreditCard size={18} /> {subscriptionLoading ? "Activating..." : "Confirm Payment & Activate"}
                      </button>
                    </div>
                  </div>
                </section>
                )}
                {qrExpanded ? (
                  <div className="qr-modal" onClick={() => setQrExpanded(false)} role="button" tabIndex={0}>
                    <img src={UPI_QR_IMAGE} alt={`Large UPI QR code for ${UPI_ID}`} />
                  </div>
                ) : null}
                <button className="button ghost" onClick={() => setActiveView("quiz")}>
                  Back to Quiz
                </button>
              </div>
            ) : !quiz ? (
              <>
                <div className="form-grid">
                  <label className="field">
                    <span className="label-row">Subject</span>
                    <input
                      className="input"
                      placeholder="e.g., Physics, History, Mathematics"
                      value={form.subject}
                      onChange={(event) => setForm({ ...form, subject: event.target.value })}
                    />
                  </label>
                  <label className="field">
                    <span className="label-row">Exam Name</span>
                    <input
                      className="input"
                      placeholder="e.g., JEE Main, UPSC, NEET"
                      value={form.examName}
                      onChange={(event) => setForm({ ...form, examName: event.target.value })}
                    />
                  </label>
                  <label className="field full">
                    <span className="label-row">
                      Chapter <span className="optional">Optional</span>
                    </span>
                    <input
                      className="input"
                      placeholder="e.g., Thermodynamics, Polity, Algebra"
                      value={form.chapter}
                      onChange={(event) => setForm({ ...form, chapter: event.target.value })}
                    />
                  </label>
                  <label className="field">
                    <span className="label-row">No. of Questions</span>
                    <input
                      className="input"
                      type="number"
                      min={1}
                      max={MAX_QUIZ_QUESTIONS}
                      inputMode="numeric"
                      value={form.numberOfQuestions}
                      onChange={(event) =>
                        setForm({ ...form, numberOfQuestions: normalizeQuestionCount(Number.parseInt(event.target.value || "5", 10)) })
                      }
                    />
                  </label>
                  <label className="field">
                    <span className="label-row">Language</span>
                    <select
                      className="select"
                      value={form.language}
                      onChange={(event) => setForm({ ...form, language: event.target.value })}
                    >
                      <option>English</option>
                      <option>Hindi</option>
                      <option>Hinglish</option>
                    </select>
                  </label>
                  <label className="field full">
                    <span className="label-row">Difficulty Level</span>
                    <select
                      className="select"
                      value={form.difficultyLevel}
                      onChange={(event) => setForm({ ...form, difficultyLevel: event.target.value })}
                    >
                      <option>Exam Pattern</option>
                      <option>Easy</option>
                      <option>Medium</option>
                      <option>Hard</option>
                      <option>Advanced</option>
                    </select>
                  </label>
                  <div className="field full">
                    <span className="label-row">Hints</span>
                    <div className="segmented">
                      <button
                        className={`segment ${form.hintsEnabled ? "active" : ""}`}
                        onClick={() => setForm({ ...form, hintsEnabled: true })}
                      >
                        Enable
                      </button>
                      <button
                        className={`segment ${!form.hintsEnabled ? "active" : ""}`}
                        onClick={() => setForm({ ...form, hintsEnabled: false })}
                      >
                        Disable
                      </button>
                    </div>
                  </div>
                  <div className="field full">
                    <span className="label-row">Negative Marking</span>
                    <div className="segmented">
                      {[
                        ["0.5", "1/2"],
                        ["0.333", "1/3"],
                        ["0.25", "1/4"],
                        ["custom", "Other"]
                      ].map(([value, label]) => (
                        <button
                          key={value}
                          className={`segment ${negativeMode === value ? "active" : ""}`}
                          onClick={() => {
                            setNegativeMode(value);
                            if (value !== "custom") {
                              setForm({ ...form, negativeMarking: Number(value) });
                            }
                          }}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  </div>
                  {negativeMode === "custom" ? (
                    <label className="field full">
                      <span className="label-row">Custom Negative Marks</span>
                      <input
                        className="input"
                        type="number"
                        min={0}
                        step="0.01"
                        placeholder="e.g., 0.66"
                        value={form.negativeMarking}
                        onChange={(event) =>
                          setForm({ ...form, negativeMarking: Number.parseFloat(event.target.value || "0") })
                        }
                      />
                    </label>
                  ) : null}
                  {error ? <div className="error field full">{error}</div> : null}
                  <div className="field full">
                    <button
                      className="button primary"
                      disabled={loading || !form.subject || !form.examName}
                      onClick={generateQuiz}
                    >
                      <Sparkles size={18} /> {loading ? "Generating..." : "Generate Quiz"}
                    </button>
                  </div>
                </div>
              </>
            ) : showResult ? (
              <div className="result">
                <span className="pill">Result</span>
                <h1 className="section-title">Test Summary</h1>
                <p className="subtitle">
                  Negative marking: {form.negativeMarking} mark per wrong answer. Final score calculation: correct -
                  wrong x negative marking.
                </p>
                <div className="result-grid">
                  <div className="result-box">
                    <span>Correct</span>
                    <strong>{result.correct}</strong>
                  </div>
                  <div className="result-box">
                    <span>Wrong</span>
                    <strong>{result.wrong}</strong>
                  </div>
                  <div className="result-box">
                    <span>Unattempted</span>
                    <strong>{result.unattempted}</strong>
                  </div>
                  <div className="result-box highlight">
                    <span>Final Score</span>
                    <strong>
                      {result.finalScore}/{quiz.questions.length}
                    </strong>
                  </div>
                </div>
                <section className="notes-result">
                  <div className="notes-head">
                    <h2>Test Notes</h2>
                    <button className="button ghost" onClick={downloadNotes} type="button">
                      <Download size={18} /> Download
                    </button>
                  </div>
                  <p>{studentNotes.trim() || "No notes written during this test."}</p>
                </section>
                <div className="nav-actions">
                  <button
                    className="button ghost"
                    onClick={() => {
                      setShowResult(false);
                      setCurrentIndex(0);
                      setShowHint(false);
                    }}
                  >
                    Review Test
                  </button>
                  <button
                    className="button primary"
                    style={{ width: "auto" }}
                    onClick={() => {
                      setQuiz(null);
                      setTargetQuestionCount(0);
                      setPrefetchNotice("");
                    }}
                  >
                    New Test
                  </button>
                </div>
              </div>
            ) : (
              <div className="quiz-stage">
                <div className="question-head">
                  <div>
                    <span className="pill">{quiz.difficulty}</span>
                    <h1 className="section-title" style={{ fontSize: "2rem", marginTop: 12 }}>
                      Question {currentIndex + 1} of {totalQuizQuestions}
                    </h1>
                  </div>
                  <button
                    className="button ghost"
                    onClick={() => {
                      setQuiz(null);
                      setTargetQuestionCount(0);
                      setPrefetchNotice("");
                    }}
                    title="New quiz"
                  >
                    <RotateCcw size={18} />
                  </button>
                </div>
                <div className="progress">
                  <span style={{ width: `${progress}%` }} />
                </div>
                <section className="panel panel-pad question-card">
                  <p className="question-text">{question?.question}</p>
                  <div className="options">
                    {question?.options.map((option, index) => (
                      <button
                        key={option}
                        className={`option ${answers[question.id] === index ? "selected" : ""}`}
                        onClick={() => setAnswers({ ...answers, [question.id]: index })}
                      >
                        {option}
                      </button>
                    ))}
                  </div>
                  {form.hintsEnabled && question ? (
                    <div className="hint-box">
                      <button className="button ghost" type="button" onClick={() => setShowHint((value) => !value)}>
                        <Lightbulb size={18} /> {showHint ? "Hide Hint" : "Show Hint"}
                      </button>
                      {showHint ? <p className="subtitle">{question.explanation}</p> : null}
                    </div>
                  ) : null}
                </section>
                <div className="nav-actions" style={{ justifyContent: "space-between" }}>
                  <button
                    className="button ghost"
                    disabled={currentIndex === 0}
                    onClick={() => {
                      setCurrentIndex((value) => Math.max(0, value - 1));
                      setShowHint(false);
                    }}
                  >
                    Previous
                  </button>
                  <strong>
                    Score: {score}/{totalQuizQuestions}
                  </strong>
                  <button
                    className="button primary"
                    style={{ width: "auto" }}
                    disabled={currentIndex === quiz.questions.length - 1 && quiz.questions.length < totalQuizQuestions}
                    onClick={() => {
                      const hasLoadedAllQuestions = quiz.questions.length >= totalQuizQuestions;
                      if (currentIndex === quiz.questions.length - 1 && hasLoadedAllQuestions) {
                        completeQuiz();
                      } else {
                        setCurrentIndex((value) => Math.min(quiz.questions.length - 1, value + 1));
                        setShowHint(false);
                      }
                    }}
                  >
                    {currentIndex === quiz.questions.length - 1 && quiz.questions.length >= totalQuizQuestions
                      ? "Submit Test"
                      : currentIndex === quiz.questions.length - 1
                        ? "Loading Next..."
                        : "Next"}
                  </button>
                </div>
                {(currentIndex === quiz.questions.length - 1 && quiz.questions.length < totalQuizQuestions) || prefetchNotice ? (
                  <div className="batch-status">
                    <Sparkles size={16} />
                    <span>{prefetchNotice || "Preparing the next questions..."}</span>
                  </div>
                ) : null}
              </div>
            )}
          </section>

          {isTakingQuiz ? (
          <aside className="side-stack">
            <section className="panel panel-pad">
              <div className="notes-head">
                <h2>Notes</h2>
                <StickyNote size={22} />
              </div>
              <textarea
                className="notes-input"
                placeholder="Write formulas, doubts, or points to revise..."
                value={studentNotes}
                onChange={(event) => setStudentNotes(event.target.value)}
              />
              <p className="subtitle">Notes will appear in your result and can be downloaded after submission.</p>
            </section>
          </aside>
          ) : null}
        </div>
      </section>
      {loading ? (
        <div className="quiz-loader-overlay" role="status" aria-live="polite" aria-modal="true">
          <div className="quiz-fun-loader">
            <div className="quiz-loader-visual">
              <span className="loader-orbit" />
              <span className="loader-core">
                <BrainCircuit size={34} />
              </span>
              <span className="loader-chip chip-one">Pattern</span>
              <span className="loader-chip chip-two">Difficulty</span>
              <span className="loader-chip chip-three">Review</span>
            </div>
            <strong>Preparing your exam-ready quiz...</strong>
            <p>JagdiSu AI is aligning the questions with the exam pattern, difficulty level, and answer quality checks.</p>
          </div>
        </div>
      ) : null}
    </main>
  );
}
