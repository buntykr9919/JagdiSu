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
  Eye,
  EyeOff,
  FileCheck,
  GraduationCap,
  Lightbulb,
  LockKeyhole,
  LogOut,
  Mail,
  MessageCircle,
  NotebookPen,
  Phone,
  RotateCcw,
  ScanText,
  Send,
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
  type AdminBillingSummary,
  type AdminDashboardResponse,
  cancelSubscription,
  createCommunityAnswer,
  createCommunityQuestion,
  createRazorpayOrder,
  downloadInvoice,
  extractHandwrittenNotes,
  evaluateHandwrittenNotes,
  generateNotesQuizWithBackend,
  generateQuizWithBackend,
  generateTopicNotes,
  loadAdminDashboard,
  loadAdminBillingSummary,
  loadCommunityQuestions,
  loadSubscriptionOverview,
  loginSuperadmin,
  likeCommunityAnswer,
  reactivateSubscription,
  submitFeedback,
  type SubscriptionOverview,
  updateFeedbackStatus,
  verifyRazorpayPayment,
  type CommunityQuestion,
  type NotesEvaluationResponse
} from "../lib/backendQuizAi";

const FREE_DAILY_LIMIT = 10;
const QUIZ_BATCH_SIZE = 5;
const MAX_QUIZ_QUESTIONS = 200;
const ATTEMPTS_STORAGE_KEY = "jdsu-attempt-history";
const UPI_ID = "buntyaxis85@ybl";
const UPI_QR_IMAGE = "/upi-qr.jpeg";
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:2000/api";
const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "";
const GOOGLE_LOGIN_ENABLED = false;
const BACKEND_RETRY_ATTEMPTS = 5;
const BACKEND_RETRY_DELAY_MS = 800;

type GoogleCredentialResponse = {
  credential?: string;
};

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
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
  fullTestMode?: boolean;
};

type Question = {
  id: string;
  question: string;
  options: string[];
  correctAnswerIndex: number;
  explanation: string;
  difficulty: string;
  subject?: string;
  topic?: string;
};

type QuizResponse = {
  quizId: string;
  examPatternSummary: string;
  difficulty: string;
  questions: Question[];
};

type Plan = "FREE" | "PRO" | "ADVANCED";
type ActiveView = "home" | "quiz" | "topic" | "notes" | "notesLab" | "community" | "subscription" | "progress" | "feedback";
type QuizMode = "customize" | "full";
type PaidPlan = Extract<Plan, "PRO" | "ADVANCED">;
type LoggedInUser = { id?: number; name?: string; email?: string; plan?: string; accessToken?: string };
type AdminView = "overview" | "users" | "content" | "website" | "analytics" | "billing" | "settings" | "feedback";

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

type TopicPerformance = {
  name: string;
  total: number;
  correct: number;
  wrong: number;
  unattempted: number;
  accuracy: number;
};

type NotesLabUpload = {
  id: string;
  name: string;
  type: string;
  sizeKb: number;
  pages: number;
  language: string;
  words: number;
  ocrRequired: boolean;
};

type KnowledgeChapter = {
  name: string;
  topics: string[];
};

type GeneratedNoteQuestion = {
  question: string;
  options: string[];
  answer: string;
  sourceTopic: string;
  sourcePage: number;
  sourceParagraph: number;
  confidence: number;
};

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function fetchBackend(input: RequestInfo | URL, init?: RequestInit) {
  let lastError: unknown;

  for (let attempt = 1; attempt <= BACKEND_RETRY_ATTEMPTS; attempt += 1) {
    try {
      return await fetch(input, init);
    } catch (error) {
      lastError = error;
      if (!(error instanceof TypeError) || attempt === BACKEND_RETRY_ATTEMPTS) {
        throw error;
      }
      await sleep(BACKEND_RETRY_DELAY_MS);
    }
  }

  throw lastError;
}

export default function HomePage() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [currentUser, setCurrentUser] = useState<LoggedInUser | null>(null);
  const [adminLoggedIn, setAdminLoggedIn] = useState(false);
  const [adminLoginMode, setAdminLoginMode] = useState(false);
  const [helpMenuOpen, setHelpMenuOpen] = useState(false);
  const [adminEmail, setAdminEmail] = useState("");
  const [adminPassword, setAdminPassword] = useState("");
  const [adminToken, setAdminToken] = useState("");
  const [adminData, setAdminData] = useState<AdminDashboardResponse | null>(null);
  const [adminBilling, setAdminBilling] = useState<AdminBillingSummary | null>(null);
  const [adminLoading, setAdminLoading] = useState(false);
  const [adminError, setAdminError] = useState("");
  const [adminView, setAdminView] = useState<AdminView>("overview");
  const [activeView, setActiveView] = useState<ActiveView>("home");
  const [plan, setPlan] = useState<Plan>("FREE");
  const [dailyUsage, setDailyUsage] = useState(0);
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [savedResultQuizId, setSavedResultQuizId] = useState("");
  const [subscriptionOverview, setSubscriptionOverview] = useState<SubscriptionOverview | null>(null);
  const [subscriptionLoading, setSubscriptionLoading] = useState(false);
  const [subscriptionNotice, setSubscriptionNotice] = useState("");
  const [selectedSubscriptionPlan, setSelectedSubscriptionPlan] = useState<Plan>("FREE");
  const [paymentReference, setPaymentReference] = useState("");
  const [upiPaymentStarted, setUpiPaymentStarted] = useState(false);
  const [qrExpanded, setQrExpanded] = useState(false);
  const [feedbackForm, setFeedbackForm] = useState({
    category: "General Feedback",
    rating: 5,
    message: ""
  });
  const [feedbackLoading, setFeedbackLoading] = useState(false);
  const [feedbackNotice, setFeedbackNotice] = useState("");
  const [communityQuestions, setCommunityQuestions] = useState<CommunityQuestion[]>([]);
  const [communityLoading, setCommunityLoading] = useState(false);
  const [communityNotice, setCommunityNotice] = useState("");
  const [communityQuestionForm, setCommunityQuestionForm] = useState({
    title: "",
    body: "",
    examType: "UPSC",
    subject: "",
    topic: ""
  });
  const [communityAnswers, setCommunityAnswers] = useState<Record<number, string>>({});
  const [quizMode, setQuizMode] = useState<QuizMode>("customize");
  const [fullTestForm, setFullTestForm] = useState({ examName: "", language: "English" });
  const [form, setForm] = useState<QuizRequest>({
    subject: "",
    examName: "",
    chapter: "",
    language: "English",
    numberOfQuestions: 0,
    difficultyLevel: "Exam Pattern",
    hintsEnabled: true,
    negativeMarking: 0.25
  });
  const [questionCountInput, setQuestionCountInput] = useState("");
  const [negativeMode, setNegativeMode] = useState("0.25");
  const [quiz, setQuiz] = useState<QuizResponse | null>(null);
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [currentIndex, setCurrentIndex] = useState(0);
  const [targetQuestionCount, setTargetQuestionCount] = useState(0);
  const [testDurationSeconds, setTestDurationSeconds] = useState(0);
  const [timeRemainingSeconds, setTimeRemainingSeconds] = useState(0);
  const [timedTestActive, setTimedTestActive] = useState(false);
  const [batchLoading, setBatchLoading] = useState(false);
  const [prefetchNotice, setPrefetchNotice] = useState("");
  const [showResult, setShowResult] = useState(false);
  const [reviewAnswersMode, setReviewAnswersMode] = useState(false);
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
  const [notesExtracting, setNotesExtracting] = useState(false);
  const [demoAnswerVisible, setDemoAnswerVisible] = useState(false);
  const [notesLabUploads, setNotesLabUploads] = useState<NotesLabUpload[]>([]);
  const [notesLabFiles, setNotesLabFiles] = useState<File[]>([]);
  const [notesLabStage, setNotesLabStage] = useState<"upload" | "analysis" | "map" | "generator" | "results">("upload");
  const [notesLabProcessing, setNotesLabProcessing] = useState(false);
  const [notesLabProgress, setNotesLabProgress] = useState(0);
  const [selectedKnowledge, setSelectedKnowledge] = useState<Record<string, boolean>>({});
  const [notesLabMode, setNotesLabMode] = useState("MCQ");
  const [notesLabExam, setNotesLabExam] = useState("UPSC");
  const [notesLabDifficulty, setNotesLabDifficulty] = useState("Medium");
  const [notesLabQuestionCount, setNotesLabQuestionCount] = useState(20);
  const [notesLabLanguage, setNotesLabLanguage] = useState("English");
  const [notesLabPlanDays, setNotesLabPlanDays] = useState(15);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [authIdentifier, setAuthIdentifier] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [authPasswordVisible, setAuthPasswordVisible] = useState(false);
  const [signupMode, setSignupMode] = useState(false);
  const [signupUsername, setSignupUsername] = useState("");
  const [signupEmail, setSignupEmail] = useState("");
  const [signupMobile, setSignupMobile] = useState("");
  const [signupPassword, setSignupPassword] = useState("");
  const [signupConfirmPassword, setSignupConfirmPassword] = useState("");
  const [signupPasswordVisible, setSignupPasswordVisible] = useState(false);
  const [signupConfirmPasswordVisible, setSignupConfirmPasswordVisible] = useState(false);
  const [signupRole, setSignupRole] = useState("");
  const [signupTermsAccepted, setSignupTermsAccepted] = useState(false);
  const prefetchInFlightRef = useRef(false);
  const googleButtonRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const savedUser = window.localStorage.getItem("jdsu-user");
    const savedPlan = window.localStorage.getItem("jdsu-plan");
    const savedAdminToken = window.localStorage.getItem("jdsu-admin-token");
    const savedAdminEmail = window.localStorage.getItem("jdsu-admin-email");
    const savedDate = window.localStorage.getItem("jdsu-usage-date");
    const savedUsage = Number(window.localStorage.getItem("jdsu-daily-usage") ?? "0");

    if (savedUser) {
      try {
        setCurrentUser(JSON.parse(savedUser) as LoggedInUser);
        setIsLoggedIn(true);
      } catch {
        window.localStorage.removeItem("jdsu-user");
      }
    }
    if (savedAdminToken) {
      setAdminToken(savedAdminToken);
      setAdminEmail(savedAdminEmail ?? "");
      setAdminLoggedIn(true);
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

    if (window.location.search.includes("admin=1")) {
      setAdminLoginMode(true);
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
    if (!isLoggedIn || !currentUser?.accessToken) {
      return;
    }
    void refreshSubscription(currentUser.accessToken);
  }, [isLoggedIn, currentUser?.accessToken]);

  useEffect(() => {
    if (!adminLoggedIn || !adminToken) {
      return;
    }
    void refreshAdminDashboard(adminToken);
  }, [adminLoggedIn, adminToken]);

  useEffect(() => {
    if (!isLoggedIn || activeView !== "community") {
      return;
    }
    void refreshCommunity();
  }, [activeView, isLoggedIn]);

  useEffect(() => {
    if (!timedTestActive || showResult || !quiz) {
      return;
    }

    const timerId = window.setInterval(() => {
      setTimeRemainingSeconds((seconds) => {
        if (seconds <= 1) {
          window.clearInterval(timerId);
          setTimedTestActive(false);
          setShowResult(true);
          return 0;
        }
        return seconds - 1;
      });
    }, 1000);

    return () => window.clearInterval(timerId);
  }, [quiz, showResult, timedTestActive]);

  useEffect(() => {
    if (!GOOGLE_LOGIN_ENABLED) {
      return;
    }
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

  const fullTestAnalysis = useMemo(() => {
    if (!quiz) {
      return null;
    }

    const attempted = result.correct + result.wrong;
    const accuracy = attempted === 0 ? 0 : Math.round((result.correct / attempted) * 100);
    const scorePercent = Math.max(0, Math.round((result.finalScore / quiz.questions.length) * 100));
    const elapsedSeconds =
      testDurationSeconds > 0
        ? Math.max(0, testDurationSeconds - timeRemainingSeconds)
        : Math.max(quiz.questions.length * 60, attempted * 75);
    const expectedSeconds = testDurationSeconds || quiz.questions.length * 72;
    const timeUsePercent = expectedSeconds === 0 ? 0 : Math.round((elapsedSeconds / expectedSeconds) * 100);
    const timeManagement =
      result.unattempted > quiz.questions.length * 0.2
        ? "Slow pacing: many questions were left unattempted."
        : timeUsePercent < 55 && result.wrong > result.correct * 0.35
          ? "Too fast: speed is creating avoidable mistakes."
          : timeUsePercent > 95 && result.unattempted > 0
            ? "Time pressure: reserve the final minutes for scan and revision."
            : "Balanced pacing: keep this speed, but review flagged questions before submit.";

    const topicMap = quiz.questions.reduce<Record<string, TopicPerformance>>((groups, question) => {
      const name = resolveQuestionTopic(question, form.examName);
      const current = groups[name] ?? { name, total: 0, correct: 0, wrong: 0, unattempted: 0, accuracy: 0 };
      const answer = answers[question.id];
      current.total += 1;
      if (answer === undefined) {
        current.unattempted += 1;
      } else if (answer === question.correctAnswerIndex) {
        current.correct += 1;
      } else {
        current.wrong += 1;
      }
      current.accuracy = Math.round((current.correct / current.total) * 100);
      groups[name] = current;
      return groups;
    }, {});

    const topicPerformance = Object.values(topicMap).sort((left, right) => {
      if (left.accuracy !== right.accuracy) {
        return left.accuracy - right.accuracy;
      }
      return right.wrong + right.unattempted - (left.wrong + left.unattempted);
    });
    const weakTopics = topicPerformance.filter((topic) => topic.accuracy < 60 || topic.wrong + topic.unattempted >= 2).slice(0, 4);
    const strongTopics = topicPerformance
      .filter((topic) => topic.accuracy >= 75 && topic.total >= 2)
      .sort((left, right) => right.accuracy - left.accuracy)
      .slice(0, 3);
    const sillyMistakes = topicPerformance
      .filter((topic) => topic.accuracy >= Math.max(accuracy, 65))
      .reduce((total, topic) => total + topic.wrong, 0);
    const guessingMistakes = Math.max(0, result.wrong - sillyMistakes);
    const rankPrediction =
      scorePercent >= 85
        ? "Top 5-10% range"
        : scorePercent >= 70
          ? "Top 10-25% range"
          : scorePercent >= 55
            ? "Middle 25-45% range"
            : scorePercent >= 40
              ? "Needs cutoff push"
              : "Foundation rebuild needed";
    const primaryWeakTopic = weakTopics[0]?.name ?? "mixed weak areas";
    const studyPlan = [
      `Revise ${primaryWeakTopic} first, then solve 25 targeted questions.`,
      `Analyze ${result.wrong} wrong answers and write the exact trap behind each mistake.`,
      result.unattempted > 0
        ? `Practice timed sets to reduce ${result.unattempted} skipped questions.`
        : "Keep a 10-minute review window for silly-mistake checks.",
      "Retake a full test after the revision cycle and compare accuracy, not only score."
    ];

    return {
      accuracy,
      timeUsePercent,
      timeManagement,
      weakTopics,
      strongTopics,
      sillyMistakes,
      guessingMistakes,
      rankPrediction,
      studyPlan
    };
  }, [answers, form.examName, form.subject, quiz, result, testDurationSeconds, timeRemainingSeconds]);

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

  const notesKnowledge = useMemo<KnowledgeChapter[]>(() => {
    if (notesLabUploads.length === 0) {
      return [];
    }

    const combinedNames = notesLabUploads.map((file) => file.name.toLowerCase()).join(" ");
    if (combinedNames.includes("polity") || combinedNames.includes("constitution")) {
      return [
        { name: "Constitution", topics: ["Making of Constitution", "Preamble", "Schedules"] },
        { name: "Fundamental Rights", topics: ["Article 14", "Article 19", "Article 21", "Article 32"] },
        { name: "DPSP", topics: ["Directive Principles", "Gandhian Principles", "Socialist Principles"] },
        { name: "Parliament", topics: ["Lok Sabha", "Rajya Sabha", "Money Bill", "Committees"] },
        { name: "Judiciary", topics: ["Supreme Court", "Judicial Review", "Writs"] }
      ];
    }
    if (combinedNames.includes("history")) {
      return [
        { name: "Ancient History", topics: ["Vedic Age", "Mauryan Empire", "Gupta Period"] },
        { name: "Medieval History", topics: ["Delhi Sultanate", "Mughals", "Bhakti Movement"] },
        { name: "Modern History", topics: ["1857 Revolt", "Congress", "Freedom Movement"] }
      ];
    }
    return [
      { name: "Core Concepts", topics: ["Definitions", "Principles", "Examples"] },
      { name: "Important Facts", topics: ["Key terms", "Important dates", "Formulae or articles"] },
      { name: "Exam Themes", topics: ["Frequently asked ideas", "Application areas", "Common traps"] }
    ];
  }, [notesLabUploads]);

  const selectedNotesTopics = useMemo(() => {
    const allTopics = notesKnowledge.flatMap((chapter) => [chapter.name, ...chapter.topics]);
    const selected = allTopics.filter((topic) => selectedKnowledge[topic]);
    return selected.length ? selected : allTopics;
  }, [notesKnowledge, selectedKnowledge]);

  const notesLabQuestions = useMemo<GeneratedNoteQuestion[]>(() => {
    const topics = selectedNotesTopics.length ? selectedNotesTopics : ["Uploaded Notes"];
    const count = Math.min(notesLabQuestionCount, 20);
    return Array.from({ length: count }, (_, index) => {
      const sourceTopic = topics[index % topics.length];
      return {
        question:
          notesLabMode === "Assertion Reason"
            ? `Assertion: ${sourceTopic} is important for ${notesLabExam}. Reason: Questions often test conceptual clarity from uploaded notes.`
            : notesLabMode === "PYQ Style"
              ? `${notesLabExam} style: Which statement about ${sourceTopic} is best supported by the uploaded notes?`
              : `Which point about ${sourceTopic} is directly supported by the uploaded notes?`,
        options: [
          `Correct concept from ${sourceTopic}`,
          `A similar but unsupported idea`,
          `A fact outside the uploaded notes`,
          `An unrelated exam distractor`
        ],
        answer: `Correct concept from ${sourceTopic}`,
        sourceTopic,
        sourcePage: (index % Math.max(1, notesLabUploads[0]?.pages ?? 1)) + 1,
        sourceParagraph: (index % 6) + 1,
        confidence: 98 - (index % 5)
      };
    });
  }, [notesLabExam, notesLabMode, notesLabQuestionCount, notesLabUploads, selectedNotesTopics]);

  const notesCoverage = useMemo(() => {
    const covered = Math.min(98, 72 + notesKnowledge.length * 5 + notesLabUploads.length * 2);
    const missed = notesLabExam === "UPSC"
      ? ["Emergency Provisions", "Constitutional Bodies", "Local Government"]
      : ["High-frequency PYQ themes", "Current examples", "Advanced application traps"];
    return { covered, missed };
  }, [notesKnowledge.length, notesLabExam, notesLabUploads.length]);

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
    const identifier = authIdentifier.trim();
    const password = authPassword.trim();

    if (!signupMode) {
      if (!identifier && !password) {
        setError("Please enter your username or email and password.");
        return;
      }
      if (!identifier) {
        setError("Please enter your username or email.");
        return;
      }
      if (!password) {
        setError("Please enter your password.");
        return;
      }
    }

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

        const response = await fetchBackend(`${API_BASE_URL}/auth/signup`, {
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

      const response = await fetchBackend(`${API_BASE_URL}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          usernameOrEmail: identifier,
          password
        })
      });

      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        const message = body.error || body.message || "";
        throw new Error(
          response.status === 401 || message.toLowerCase().includes("invalid")
            ? "Invalid username/email or password."
            : message || "Login failed. Please try again."
        );
      }

      const user = await response.json();
      saveLoggedInUser(user);
    } catch (err) {
      setError(
        err instanceof TypeError
          ? "Backend is not reachable. Run .\\run-app.ps1 so Spring Boot starts on port 2000 and MySQL is checked."
          : err instanceof Error
            ? err.message
            : "Login failed. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }

  function saveLoggedInUser(user: LoggedInUser) {
    window.localStorage.setItem("jdsu-user", JSON.stringify(user));
    window.localStorage.setItem("jdsu-plan", user.plan ?? "FREE");
    setCurrentUser(user);
    setPlan(user.plan === "ADVANCED" ? "ADVANCED" : user.plan === "PRO" ? "PRO" : "FREE");
    setIsLoggedIn(true);
    setActiveView("home");
  }

  async function loginAsSuperadmin() {
    setError("");
    if (!adminEmail.trim() || !adminPassword.trim()) {
      setError("Enter the superadmin email and password.");
      return;
    }

    setLoading(true);
    try {
      const admin = await loginSuperadmin(adminEmail.trim(), adminPassword.trim());
      window.localStorage.setItem("jdsu-admin-token", admin.token);
      window.localStorage.setItem("jdsu-admin-email", admin.email);
      setAdminToken(admin.token);
      setAdminEmail(admin.email);
      setAdminLoggedIn(true);
      setAdminLoginMode(false);
      setIsLoggedIn(false);
      window.localStorage.removeItem("jdsu-user");
      window.localStorage.removeItem("jdsu-plan");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Superadmin login failed.");
    } finally {
      setLoading(false);
    }
  }

  async function refreshAdminDashboard(token = adminToken) {
    if (!token) {
      return;
    }

    setAdminLoading(true);
    setAdminError("");
    try {
      const [dashboard, billing] = await Promise.all([
        loadAdminDashboard(token),
        loadAdminBillingSummary(token)
      ]);
      setAdminData(dashboard);
      setAdminBilling(billing);
    } catch (err) {
      setAdminError(err instanceof Error ? err.message : "Admin dashboard could not be loaded.");
    } finally {
      setAdminLoading(false);
    }
  }

  async function refreshSubscription(accessToken = currentUser?.accessToken ?? "") {
    if (!accessToken) {
      return;
    }
    try {
      const overview = await loadSubscriptionOverview(accessToken);
      const nextPlan = overview.planCode === "ADVANCED" ? "ADVANCED" : overview.planCode === "PRO" ? "PRO" : "FREE";
      setSubscriptionOverview(overview);
      setPlan(nextPlan);
      setSelectedSubscriptionPlan(nextPlan);
      window.localStorage.setItem("jdsu-plan", nextPlan);
    } catch (err) {
      setSubscriptionNotice(err instanceof Error ? err.message : "Subscription details could not be loaded.");
    }
  }

  async function changeFeedbackStatus(id: number, status: string) {
    if (!adminToken) {
      return;
    }
    setAdminError("");
    try {
      await updateFeedbackStatus(adminToken, id, status);
      await refreshAdminDashboard(adminToken);
    } catch (err) {
      setAdminError(err instanceof Error ? err.message : "Feedback status could not be updated.");
    }
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
      const response = await fetchBackend(`${API_BASE_URL}/auth/google`, {
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
    window.localStorage.removeItem("jdsu-admin-token");
    window.localStorage.removeItem("jdsu-admin-email");
    setIsLoggedIn(false);
    setAdminLoggedIn(false);
    setCurrentUser(null);
    setAdminToken("");
    setAdminData(null);
    setActiveView("home");
    setQuiz(null);
    setAnswers({});
    setCurrentIndex(0);
    setShowResult(false);
    setTimedTestActive(false);
    setTestDurationSeconds(0);
    setTimeRemainingSeconds(0);
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
    if (notesLoading || !notesForm.examName.trim() || !notesForm.answerText.trim()) {
      return;
    }

    setError("");
    setNotesLoading(true);
    setNotesResult(null);
    setDemoAnswerVisible(false);
    try {
      const response = await evaluateHandwrittenNotes({
        ...notesForm,
        examName: notesForm.examName.trim(),
        file: notesFile && notesFile.type.startsWith("image/") ? notesFile : null
      });
      setNotesResult(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Handwritten notes could not be checked.");
    } finally {
      setNotesLoading(false);
    }
  }

  async function sendFeedback() {
    if (feedbackLoading || !feedbackForm.message.trim()) {
      setFeedbackNotice("Please write your feedback before submitting.");
      return;
    }

    setFeedbackLoading(true);
    setFeedbackNotice("");
    try {
      const response = await submitFeedback({
        userId: currentUser?.id ?? null,
        userName: currentUser?.name ?? null,
        userEmail: currentUser?.email ?? null,
        category: feedbackForm.category,
        rating: feedbackForm.rating,
        message: feedbackForm.message.trim()
      });
      setFeedbackNotice(response.message);
      setFeedbackForm((current) => ({ ...current, message: "" }));
    } catch (err) {
      setFeedbackNotice(err instanceof Error ? err.message : "Feedback could not be submitted.");
    } finally {
      setFeedbackLoading(false);
    }
  }

  async function refreshCommunity() {
    setCommunityLoading(true);
    setCommunityNotice("");
    try {
      setCommunityQuestions(await loadCommunityQuestions());
    } catch (err) {
      setCommunityNotice(err instanceof Error ? err.message : "Community questions could not be loaded.");
    } finally {
      setCommunityLoading(false);
    }
  }

  async function postCommunityQuestion() {
    const title = communityQuestionForm.title.trim();
    const body = communityQuestionForm.body.trim();
    const subject = communityQuestionForm.subject.trim();
    if (!title || !body || !subject) {
      setCommunityNotice("Question title, details, and subject are required.");
      return;
    }

    setCommunityLoading(true);
    setCommunityNotice("");
    try {
      const question = await createCommunityQuestion({
        userId: currentUser?.id ?? null,
        userName: currentUser?.name ?? currentUser?.email ?? "Student",
        title,
        body,
        examType: communityQuestionForm.examType,
        subject,
        topic: communityQuestionForm.topic.trim()
      });
      setCommunityQuestions((current) => [question, ...current]);
      setCommunityQuestionForm({ title: "", body: "", examType: "UPSC", subject: "", topic: "" });
      setCommunityNotice("Question posted to the community.");
    } catch (err) {
      setCommunityNotice(err instanceof Error ? err.message : "Question could not be posted.");
    } finally {
      setCommunityLoading(false);
    }
  }

  async function postCommunityAnswer(questionId: number) {
    const body = communityAnswers[questionId]?.trim();
    if (!body) {
      setCommunityNotice("Write an answer first.");
      return;
    }

    setCommunityLoading(true);
    setCommunityNotice("");
    try {
      const answer = await createCommunityAnswer(questionId, {
        userId: currentUser?.id ?? null,
        userName: currentUser?.name ?? currentUser?.email ?? "Student",
        body
      });
      setCommunityQuestions((current) =>
        current.map((question) =>
          question.id === questionId
            ? { ...question, answerCount: question.answerCount + 1, answers: [...question.answers, answer] }
            : question
        )
      );
      setCommunityAnswers((current) => ({ ...current, [questionId]: "" }));
    } catch (err) {
      setCommunityNotice(err instanceof Error ? err.message : "Answer could not be posted.");
    } finally {
      setCommunityLoading(false);
    }
  }

  async function likeAnswer(questionId: number, answerId: number) {
    setCommunityNotice("");
    try {
      const likedAnswer = await likeCommunityAnswer(answerId);
      setCommunityQuestions((current) =>
        current.map((question) => {
          if (question.id !== questionId) {
            return question;
          }
          return {
            ...question,
            answers: question.answers
              .map((answer) => (answer.id === answerId ? likedAnswer : answer))
              .sort((left, right) => right.upvotes - left.upvotes || new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime())
          };
        })
      );
    } catch (err) {
      setCommunityNotice(err instanceof Error ? err.message : "Like could not be added.");
    }
  }

  async function extractUploadedNotes(nextFile: File | null) {
    if (notesExtracting || !nextFile) {
      return;
    }

    setError("");
    setNotesResult(null);
    setDemoAnswerVisible(false);
    setNotesExtracting(true);
    try {
      const extracted = await extractHandwrittenNotes({
        file: nextFile,
        language: notesForm.language
      });
      setNotesForm((current) => ({
        ...current,
        questionPaperText: extracted.questionText || current.questionPaperText,
        answerText: extracted.answerText || current.answerText
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Uploaded PDF could not be read. You can type the text manually.");
    } finally {
      setNotesExtracting(false);
    }
  }

  function analyzeNotesLabFiles(files: FileList | File[]) {
    const fileArray = Array.from(files);
    if (fileArray.length === 0) {
      return;
    }
    const supportedFiles = fileArray.filter((file) => {
      const name = file.name.toLowerCase();
      return file.type.startsWith("image/") || file.type === "application/pdf" || name.endsWith(".pdf");
    });
    if (supportedFiles.length === 0) {
      setError("Notes quiz ke liye PDF ya image upload karo. DOCX/PPT abhi source-check ke saath supported nahi hai.");
      return;
    }

    setNotesLabFiles(supportedFiles.slice(0, 5));
    setError("");
    setNotesLabProcessing(true);
    setNotesLabProgress(12);
    setNotesLabStage("analysis");

    const analyzedUploads = supportedFiles.slice(0, 5).map((file, index) => {
      const lowerName = file.name.toLowerCase();
      const isImageFile = file.type.startsWith("image/") || /\.(png|jpe?g|webp)$/i.test(file.name);
      const isPresentation = /\.(ppt|pptx)$/i.test(file.name);
      const pages = isImageFile ? 1 : isPresentation ? Math.max(8, Math.round(file.size / 180000)) : Math.max(1, Math.round(file.size / 120000));
      return {
        id: `${file.name}-${file.size}-${Date.now()}-${index}`,
        name: file.name,
        type: file.type || file.name.split(".").pop()?.toUpperCase() || "Document",
        sizeKb: Math.max(1, Math.round(file.size / 1024)),
        pages,
        language: lowerName.includes("hindi") || lowerName.includes("hin") ? "Hindi" : "English",
        words: Math.max(250, pages * (isImageFile ? 140 : 430)),
        ocrRequired: isImageFile || lowerName.includes("handwritten") || lowerName.includes("scan")
      };
    });

    window.setTimeout(() => setNotesLabProgress(42), 250);
    window.setTimeout(() => setNotesLabProgress(76), 650);
    window.setTimeout(() => {
      setNotesLabUploads((current) => [...analyzedUploads, ...current].slice(0, 8));
      setSelectedKnowledge({});
      setNotesLabProgress(100);
      setNotesLabProcessing(false);
      setNotesLabStage("analysis");
    }, 1050);
  }

  function toggleKnowledgeItem(name: string) {
    setSelectedKnowledge((current) => ({ ...current, [name]: !current[name] }));
  }

  function selectEntireNotes() {
    const allSelected = notesKnowledge.flatMap((chapter) => [chapter.name, ...chapter.topics]);
    setSelectedKnowledge(Object.fromEntries(allSelected.map((topic) => [topic, true])));
  }

  function buildOneLiners() {
    const count = notesLabQuestionCount === 10 ? 50 : notesLabQuestionCount === 20 ? 100 : 200;
    return Array.from({ length: Math.min(count, 12) }, (_, index) => {
      const topic = selectedNotesTopics[index % Math.max(1, selectedNotesTopics.length)] ?? "Uploaded Notes";
      return { question: `Q. ${topic} ka key exam fact kya hai?`, answer: `A. ${topic} uploaded notes ka high-value revision point hai.` };
    });
  }

  function buildStudyPlan() {
    const chunks = notesLabPlanDays === 7 ? 4 : notesLabPlanDays === 15 ? 6 : notesLabPlanDays === 30 ? 8 : 10;
    return Array.from({ length: chunks }, (_, index) => {
      const topic = selectedNotesTopics[index % Math.max(1, selectedNotesTopics.length)] ?? "Uploaded Notes";
      return `Day ${index + 1}: Revise ${topic}, solve notes-based MCQs, then mark weak facts for retest.`;
    });
  }

  async function startNotesBasedQuiz() {
    if (loading) {
      return;
    }

    if (notesLabUploads.length === 0 || notesLabFiles.length === 0) {
      setError("Please upload notes first.");
      return;
    }

    const totalQuestions = normalizeQuestionCount(notesLabQuestionCount);
    const firstBatchSize = Math.min(QUIZ_BATCH_SIZE, totalQuestions);
    const uploadNames = notesLabUploads.map((file) => file.name).join(", ");
    const notesRequest: QuizRequest = {
      subject: "Uploaded Notes",
      examName: "Notes Based Quiz",
      chapter: `Uploaded notes: ${uploadNames}. Generate questions only from the readable part of these notes. Start with the first part and keep later batches from later note sections.`,
      language: notesLabLanguage,
      numberOfQuestions: firstBatchSize,
      difficultyLevel: "Exam Pattern",
      hintsEnabled: true,
      negativeMarking: 0,
      totalQuestions,
      batchNumber: 1,
      previousQuestionSummaries: [],
      fullTestMode: false
    };

    setError("");
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
    setTimedTestActive(false);
    setTestDurationSeconds(0);
    setTimeRemainingSeconds(0);
    setQuestionCountInput(String(totalQuestions));
    setNegativeMode("custom");
    setForm(notesRequest);

    try {
      const generatedQuiz = await generateNotesQuizWithBackend({
        files: notesLabFiles,
        language: notesLabLanguage,
        numberOfQuestions: firstBatchSize,
        totalQuestions,
        batchNumber: 1,
        previousQuestionSummaries: []
      });
      setQuiz(generatedQuiz);
      setActiveView("quiz");
      if (!isPro) {
        const nextUsage = dailyUsage + 1;
        setDailyUsage(nextUsage);
        window.localStorage.setItem("jdsu-usage-date", todayKey());
        window.localStorage.setItem("jdsu-daily-usage", String(nextUsage));
      }
    } catch (err) {
      setQuiz(null);
      setError(err instanceof Error ? err.message : "Notes based quiz could not be generated.");
    } finally {
      setLoading(false);
    }
  }

  function normalizeQuestionCount(value: number) {
    if (!Number.isFinite(value)) {
      return QUIZ_BATCH_SIZE;
    }
    return Math.min(MAX_QUIZ_QUESTIONS, Math.max(1, Math.round(value)));
  }

  function parseQuestionCountInput() {
    const parsed = Number.parseInt(questionCountInput, 10);
    return Number.isFinite(parsed) ? normalizeQuestionCount(parsed) : 0;
  }

  function resolveFullTestConfig(examName: string) {
    const exam = examName.toLowerCase();
    if (exam.includes("neet")) {
      return { totalQuestions: 180, durationMinutes: 180, negativeMarking: 1 };
    }
    if (exam.includes("jee")) {
      return { totalQuestions: 75, durationMinutes: 180, negativeMarking: 1 };
    }
    if (exam.includes("upsc") || exam.includes("civil")) {
      return { totalQuestions: 100, durationMinutes: 120, negativeMarking: 0.66 };
    }
    if (exam.includes("bpsc") || exam.includes("pcs")) {
      return { totalQuestions: 150, durationMinutes: 120, negativeMarking: 0 };
    }
    if (exam.includes("ssc cgl") || exam.includes("ssc chsl")) {
      return { totalQuestions: 100, durationMinutes: 60, negativeMarking: 0.5 };
    }
    if (exam.includes("rrb") || exam.includes("railway")) {
      return { totalQuestions: 100, durationMinutes: 90, negativeMarking: 0.33 };
    }
    if (exam.includes("gate")) {
      return { totalQuestions: 65, durationMinutes: 180, negativeMarking: 0.33 };
    }
    if (exam.includes("cat")) {
      return { totalQuestions: 66, durationMinutes: 120, negativeMarking: 1 };
    }
    if (exam.includes("clat")) {
      return { totalQuestions: 120, durationMinutes: 120, negativeMarking: 0.25 };
    }
    if (exam.includes("nda")) {
      return { totalQuestions: 120, durationMinutes: 150, negativeMarking: 0.33 };
    }
    if (exam.includes("cds")) {
      return { totalQuestions: 120, durationMinutes: 120, negativeMarking: 0.33 };
    }
    if (exam.includes("bank") || exam.includes("po") || exam.includes("clerk")) {
      return { totalQuestions: 100, durationMinutes: 60, negativeMarking: 0.25 };
    }
    return { totalQuestions: 100, durationMinutes: 120, negativeMarking: 0.25 };
  }

  function formatTimer(seconds: number) {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const remainingSeconds = seconds % 60;
    return [hours, minutes, remainingSeconds].map((value) => String(value).padStart(2, "0")).join(":");
  }

  function summarizePreviousQuestions(questions: Question[]) {
    return questions
      .slice(-25)
      .map((question, index) => `${index + 1}. ${question.question.slice(0, 220)}`);
  }

  function resolveQuestionTopic(question: Question, examName: string) {
    const directTopic = question.topic?.trim() || question.subject?.trim();
    if (directTopic) {
      return directTopic;
    }

    const text = `${question.question} ${question.explanation}`.toLowerCase();
    if (text.includes("algebra") || text.includes("ratio") || text.includes("percentage") || text.includes("profit")) {
      return "Math";
    }
    if (text.includes("reasoning") || text.includes("series") || text.includes("coding") || text.includes("analogy")) {
      return "Reasoning";
    }
    if (text.includes("history") || text.includes("mughal") || text.includes("vedic") || text.includes("freedom")) {
      return "GS - History";
    }
    if (text.includes("polity") || text.includes("constitution") || text.includes("parliament")) {
      return "GS - Polity";
    }
    if (text.includes("geography") || text.includes("river") || text.includes("climate")) {
      return "GS - Geography";
    }
    if (text.includes("current affairs") || text.includes("recent") || text.includes("scheme")) {
      return "Current Affairs";
    }
    if (examName.toLowerCase().includes("rrb") || examName.toLowerCase().includes("railway")) {
      return "Railway General Awareness";
    }
    return form.subject || "General";
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
      const previousQuestionSummaries = summarizePreviousQuestions(currentQuiz.questions);
      const generatedBatch =
        form.examName === "Notes Based Quiz" && notesLabFiles.length > 0
          ? await generateNotesQuizWithBackend({
              files: notesLabFiles,
              language: form.language,
              numberOfQuestions: batchSize,
              totalQuestions,
              batchNumber,
              previousQuestionSummaries
            })
          : await generateQuizWithBackend({
              ...form,
              numberOfQuestions: batchSize,
              totalQuestions,
              batchNumber,
              previousQuestionSummaries
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
  }, [currentIndex, notesLabFiles, quiz, showResult, targetQuestionCount]);

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

    const totalQuestions = parseQuestionCountInput();
    if (!totalQuestions) {
      setError("Please enter the number of questions.");
      return;
    }
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
    setTimedTestActive(false);
    setTestDurationSeconds(0);
    setTimeRemainingSeconds(0);
    setQuestionCountInput(String(totalQuestions));
    setForm((current) => ({ ...current, numberOfQuestions: totalQuestions, fullTestMode: false }));
    try {
      const generatedQuiz = await generateQuizWithBackend({
        ...form,
        numberOfQuestions: firstBatchSize,
        totalQuestions,
        batchNumber: 1,
        previousQuestionSummaries: [],
        fullTestMode: false
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

  async function generateFullTest() {
    if (loading) {
      return;
    }

    const examName = fullTestForm.examName.trim();
    if (!examName) {
      setError("Please enter the exam name.");
      return;
    }

    if (!isPro && dailyUsage >= FREE_DAILY_LIMIT) {
      setActiveView("subscription");
      setSubscriptionNotice("You have used all 10 free quizzes for today. Subscribe to Pro to continue.");
      return;
    }

    const fullTestConfig = resolveFullTestConfig(examName);
    const totalQuestions = normalizeQuestionCount(fullTestConfig.totalQuestions);
    const firstBatchSize = Math.min(QUIZ_BATCH_SIZE, totalQuestions);
    const durationSeconds = fullTestConfig.durationMinutes * 60;
    const fullTestRequest: QuizRequest = {
      subject: "Full Syllabus",
      examName,
      chapter: "",
      language: fullTestForm.language,
      numberOfQuestions: firstBatchSize,
      difficultyLevel: "Exam Pattern",
      hintsEnabled: false,
      negativeMarking: fullTestConfig.negativeMarking,
      totalQuestions,
      batchNumber: 1,
      previousQuestionSummaries: [],
      fullTestMode: true
    };

    setError("");
    setLoading(true);
    setBatchLoading(false);
    setTargetQuestionCount(totalQuestions);
    setTestDurationSeconds(durationSeconds);
    setTimeRemainingSeconds(durationSeconds);
    setTimedTestActive(false);
    setPrefetchNotice("");
    prefetchInFlightRef.current = false;
    setAnswers({});
    setCurrentIndex(0);
    setShowResult(false);
    setShowHint(false);
    setStudentNotes("");
    setSavedResultQuizId("");
    setQuestionCountInput(String(totalQuestions));
    setNegativeMode("custom");
    setForm(fullTestRequest);

    try {
      const generatedQuiz = await generateQuizWithBackend(fullTestRequest);
      setQuiz(generatedQuiz);
      setTimedTestActive(true);
      if (!isPro) {
        const nextUsage = dailyUsage + 1;
        setDailyUsage(nextUsage);
        window.localStorage.setItem("jdsu-usage-date", todayKey());
        window.localStorage.setItem("jdsu-daily-usage", String(nextUsage));
      }
    } catch (err) {
      setQuiz(null);
      setTimedTestActive(false);
      setError(err instanceof Error ? err.message : "The AI full test could not be generated.");
    } finally {
      setLoading(false);
    }
  }

  function completeQuiz() {
    setReviewAnswersMode(false);
    if (!quiz || savedResultQuizId === quiz.quizId) {
      setShowResult(true);
      setTimedTestActive(false);
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
    setTimedTestActive(false);
  }

  function openAnswerReview() {
    setShowResult(false);
    setReviewAnswersMode(true);
    setCurrentIndex(0);
    setShowHint(false);
    setTimedTestActive(false);
  }

  function buildMemoryTrick(question: Question) {
    const correctAnswer = question.options[question.correctAnswerIndex] ?? "the correct option";
    const cleanAnswer = correctAnswer.replace(/^[A-D]\.?\s*/i, "").trim();
    const shortAnswer = cleanAnswer.length > 48 ? `${cleanAnswer.slice(0, 48)}...` : cleanAnswer;
    const firstWords = cleanAnswer
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 4)
      .map((word) => word[0]?.toUpperCase())
      .join("");

    if (firstWords.length >= 2) {
      return `Desi trick: answer ke initials "${firstWords}" yaad rakho aur socho exam hall me chai-wala bol raha hai, "${shortAnswer}" hi pakka jawab hai. Thoda filmy, par recall fast hota hai.`;
    }

    return `Funny trick: "${shortAnswer}" ko apne dimaag ka VIP guest bana do. Jab bhi question ka keyword dikhe, bolo "VIP answer aa gaya" aur isi option ko recall karo.`;
  }

  function buildDeepExplanation(question: Question) {
    const correctAnswer = question.options[question.correctAnswerIndex] ?? "the correct option";
    const baseExplanation = question.explanation?.trim() || "The correct answer follows directly from the core concept tested in this question.";
    return {
      correctAnswer,
      concept: `Correct answer: ${correctAnswer}`,
      explanation: baseExplanation,
      memoryTrick: buildMemoryTrick(question)
    };
  }

  function getOptionClass(question: Question, index: number) {
    const selectedAnswer = answers[question.id];
    const classes = ["option"];

    if (!reviewAnswersMode && selectedAnswer === index) {
      classes.push("selected");
    }

    if (reviewAnswersMode) {
      if (index === question.correctAnswerIndex) {
        classes.push("correct-answer");
      }
      if (selectedAnswer === index && selectedAnswer !== question.correctAnswerIndex) {
        classes.push("wrong-answer");
      }
      if (selectedAnswer === index) {
        classes.push("selected-review");
      }
    }

    return classes.join(" ");
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

  function downloadDemoAnswer() {
    if (!notesResult) {
      return;
    }

    const lines = [
      "JAGDISU",
      "AI Exam Answer Studio",
      "Document: Corrected Demo Answer",
      `Exam: ${notesResult.examName}`,
      `Score: ${notesResult.score}/${notesResult.maxScore}`,
      `Language: ${notesForm.language}`,
      "",
      "Question",
      notesForm.questionPaperText.trim() || "Question text was not provided.",
      "",
      "Corrected Demo Answer",
      notesResult.idealAnswer || "No demo answer was generated.",
      "",
      "Mistakes Fixed",
      ...notesResult.mistakes.map((item, index) => `${index + 1}. ${item}`),
      "",
      "JagdiSu TM - Generated for study and revision."
    ];
    const blob = createNotesPdf(lines);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `jagdisu-demo-answer-${Date.now()}.pdf`;
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

  function formatBillingDate(value?: string | null) {
    if (!value) {
      return "Not scheduled";
    }
    return new Date(value).toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric"
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
      setTimedTestActive(false);
      setTestDurationSeconds(0);
      setTimeRemainingSeconds(0);
      return;
    }
    if (activeView !== "home") {
      setActiveView("home");
    }
  }

  function goHome() {
    setError("");
    setActiveView("home");
    setQuiz(null);
    setAnswers({});
    setCurrentIndex(0);
    setTargetQuestionCount(0);
    setPrefetchNotice("");
    setShowResult(false);
    setShowHint(false);
    setTimedTestActive(false);
    setTestDurationSeconds(0);
    setTimeRemainingSeconds(0);
  }

  const selectedPaidPlan = selectedSubscriptionPlan === "ADVANCED" ? "ADVANCED" : "PRO";
  const selectedPlanPrice = selectedSubscriptionPlan === "ADVANCED" ? 49 : 20;
  const upiPaymentUrl = `upi://pay?pa=${encodeURIComponent(UPI_ID)}&pn=${encodeURIComponent("JagdiSu")}&am=${selectedPlanPrice}&cu=INR&tn=${encodeURIComponent(`${selectedPaidPlan} subscription`)}`;
  const fullTestPreview = resolveFullTestConfig(fullTestForm.examName);

  async function startPlanCheckout(nextPlan: PaidPlan) {
    if (!currentUser?.accessToken) {
      setSubscriptionNotice("Login session missing hai. Please login again.");
      return;
    }

    setSubscriptionLoading(true);
    setError("");
    setSubscriptionNotice("");

    try {
      const order = await createRazorpayOrder(currentUser.accessToken, nextPlan);
      if (!order.keyId || order.status.startsWith("MOCK")) {
        await verifyRazorpayPayment(currentUser.accessToken, {
          razorpayOrderId: order.razorpayOrderId,
          razorpayPaymentId: `pay_mock_${Date.now()}`,
          razorpaySignature: "mock_signature"
        });
        await refreshSubscription(currentUser.accessToken);
        setSubscriptionNotice(`${nextPlan === "ADVANCED" ? "Advanced" : "Pro"} plan active ho gaya.`);
        return;
      }

      await loadRazorpayCheckout();
      await new Promise<void>((resolve, reject) => {
        const RazorpayCheckout = window.Razorpay;
        if (!RazorpayCheckout) {
          reject(new Error("Razorpay checkout load nahi hua."));
          return;
        }
        const checkout = new RazorpayCheckout({
          key: order.keyId,
          amount: order.amountPaise,
          currency: order.currency,
          name: "JagdiSu",
          description: `${nextPlan} subscription`,
          order_id: order.razorpayOrderId,
          handler: async (response: {
            razorpay_order_id: string;
            razorpay_payment_id: string;
            razorpay_signature: string;
          }) => {
            try {
              await verifyRazorpayPayment(currentUser.accessToken ?? "", {
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature
              });
              await refreshSubscription(currentUser.accessToken ?? "");
              setSubscriptionNotice(`${nextPlan === "ADVANCED" ? "Advanced" : "Pro"} plan active ho gaya.`);
              resolve();
            } catch (err) {
              reject(err);
            }
          },
          prefill: {
            name: currentUser.name ?? "",
            email: currentUser.email ?? ""
          },
          theme: { color: "#0f766e" }
        });
        checkout.open();
      });
    } catch (err) {
      setSubscriptionNotice(err instanceof Error ? err.message : "The subscription could not be activated.");
    } finally {
      setSubscriptionLoading(false);
    }
  }

  async function cancelCurrentSubscription() {
    if (!currentUser?.accessToken) {
      setSubscriptionNotice("Login session missing hai. Please login again.");
      return;
    }
    setSubscriptionLoading(true);
    setSubscriptionNotice("");
    try {
      const response = await cancelSubscription(currentUser.accessToken);
      await refreshSubscription(currentUser.accessToken);
      setSubscriptionNotice(response.message);
    } catch (err) {
      setSubscriptionNotice(err instanceof Error ? err.message : "Subscription cancel nahi ho paya.");
    } finally {
      setSubscriptionLoading(false);
    }
  }

  async function reactivateCurrentSubscription() {
    if (!currentUser?.accessToken) {
      setSubscriptionNotice("Login session missing hai. Please login again.");
      return;
    }
    setSubscriptionLoading(true);
    setSubscriptionNotice("");
    try {
      const response = await reactivateSubscription(currentUser.accessToken);
      await refreshSubscription(currentUser.accessToken);
      setSubscriptionNotice(response.message);
    } catch (err) {
      setSubscriptionNotice(err instanceof Error ? err.message : "Subscription reactivate nahi ho paya.");
    } finally {
      setSubscriptionLoading(false);
    }
  }

  async function downloadBillingInvoice(invoiceNumber: string) {
    if (!currentUser?.accessToken) {
      setSubscriptionNotice("Login session missing hai. Please login again.");
      return;
    }
    try {
      const blob = await downloadInvoice(currentUser.accessToken, invoiceNumber);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${invoiceNumber}.html`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setSubscriptionNotice(err instanceof Error ? err.message : "Invoice download nahi ho paya.");
    }
  }

  function loadRazorpayCheckout() {
    if (window.Razorpay) {
      return Promise.resolve();
    }
    return new Promise<void>((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "https://checkout.razorpay.com/v1/checkout.js";
      script.onload = () => resolve();
      script.onerror = () => reject(new Error("Razorpay checkout script load nahi hua."));
      document.body.appendChild(script);
    });
  }

  if (adminLoggedIn) {
    const metrics = adminData?.metrics;
    const paidUsers = metrics ? metrics.proUsers + metrics.advancedUsers : 0;
    const adminNav: Array<{ id: AdminView; label: string; icon: typeof BarChart3 }> = [
      { id: "overview", label: "Dashboard", icon: BarChart3 },
      { id: "users", label: "Users", icon: UsersRound },
      { id: "content", label: "Content", icon: BookOpen },
      { id: "website", label: "Website", icon: ScanText },
      { id: "analytics", label: "Analytics", icon: TrendingUp },
      { id: "billing", label: "Billing", icon: CreditCard },
      { id: "settings", label: "Settings", icon: Crown },
      { id: "feedback", label: "Support Messages", icon: ClipboardList }
    ];
    const quickActions = ["Add New Page", "Create User", "Manage Settings", "Review Feedback", "View Website"];

    return (
      <main className="admin-console">
        <aside className="admin-sidebar">
          <button className="admin-logo" type="button" onClick={() => setAdminView("overview")}>
            <Crown size={24} />
            <strong>JAGDISU</strong>
          </button>
          <nav className="admin-nav">
            {adminNav.map((item) => {
              const Icon = item.icon;
              return (
                <button
                  className={adminView === item.id ? "active" : ""}
                  key={item.id}
                  onClick={() => setAdminView(item.id)}
                  type="button"
                >
                  <Icon size={18} />
                  {item.label}
                </button>
              );
            })}
          </nav>
        </aside>

        <section className="admin-main">
          <header className="admin-header">
            <input className="admin-search" placeholder="Search anything..." />
            <div className="admin-header-actions">
              <button className="mini-action" type="button" onClick={() => refreshAdminDashboard()} disabled={adminLoading}>
                <RotateCcw size={16} /> Refresh
              </button>
              <div className="admin-avatar">
                <span>{adminEmail.slice(0, 1).toUpperCase()}</span>
                <div>
                  <strong>JagdiSu</strong>
                  <em>Superadmin</em>
                </div>
              </div>
              <button className="mini-action" type="button" onClick={logout}>
                <LogOut size={16} /> Logout
              </button>
            </div>
          </header>

          {adminError ? <div className="error">{adminError}</div> : null}

          {adminView === "overview" ? (
            <>
              <div className="admin-metrics">
                <div className="admin-kpi"><span>Total Users</span><strong>{metrics?.totalUsers ?? "--"}</strong><em>+12.5% from last month</em><UsersRound size={28} /></div>
                <div className="admin-kpi"><span>Paid Users</span><strong>{paidUsers}</strong><em>Pro and Advanced subscribers</em><Crown size={28} /></div>
                <div className="admin-kpi"><span>Total Feedback</span><strong>{metrics?.totalFeedback ?? "--"}</strong><em>{metrics?.newFeedback ?? 0} new messages</em><ClipboardList size={28} /></div>
                <div className="admin-kpi"><span>Average Rating</span><strong>{metrics ? `${metrics.averageRating}/5` : "--"}</strong><em>User satisfaction signal</em><Sparkles size={28} /></div>
              </div>

              <div className="admin-dashboard-grid">
                <section className="admin-card wide">
                  <div className="admin-card-head"><h2>Website Traffic</h2><span>Last 30 Days</span></div>
                  <div className="admin-chart">
                    {[34, 46, 42, 68, 64, 82, 58, 48, 74, 80, 62, 70].map((height, index) => (
                      <span key={index} style={{ height: `${height}%` }} />
                    ))}
                  </div>
                </section>
                <section className="admin-card">
                  <div className="admin-card-head"><h2>Top Pages</h2><span>Views</span></div>
                  {["/", "/signup", "/quiz", "/study", "/feedback"].map((page, index) => (
                    <div className="admin-table-row" key={page}><span>{page}</span><strong>{[25489, 10256, 8965, 6325, 4125][index].toLocaleString()}</strong></div>
                  ))}
                </section>
                <section className="admin-card">
                  <div className="admin-card-head"><h2>Recent Users</h2><span>View All</span></div>
                  {adminData?.recentUsers.slice(0, 5).map((user) => (
                    <div className="user-row" key={user.id}><div><strong>{user.name}</strong><span>{user.email}</span></div><em>{user.plan}</em></div>
                  )) || <div className="empty-state">No users yet.</div>}
                </section>
                <section className="admin-card">
                  <div className="admin-card-head"><h2>Users by Plan</h2><span>{metrics?.totalUsers ?? 0} users</span></div>
                  <div className="plan-donut"><strong>{metrics?.totalUsers ?? 0}</strong><span>Total Users</span></div>
                  <div className="plan-breakdown compact">
                    <div><span>Free</span><strong>{metrics?.freeUsers ?? "--"}</strong></div>
                    <div><span>Pro</span><strong>{metrics?.proUsers ?? "--"}</strong></div>
                    <div><span>Advanced</span><strong>{metrics?.advancedUsers ?? "--"}</strong></div>
                  </div>
                </section>
                <section className="admin-card">
                  <div className="admin-card-head"><h2>System Status</h2><span>Live</span></div>
                  {["Server Status: Active", "Database: Connected", "SSL Certificate: Valid", "Backup Status: Up to date"].map((status) => (
                    <div className="admin-table-row" key={status}><span>{status.split(":")[0]}</span><strong>{status.split(":")[1].trim()}</strong></div>
                  ))}
                </section>
                <section className="admin-card">
                  <div className="admin-card-head"><h2>Quick Actions</h2><span>Owner tools</span></div>
                  {quickActions.map((action) => (
                    <button className="admin-action" key={action} type="button">{action}</button>
                  ))}
                </section>
              </div>
            </>
          ) : adminView === "billing" ? (
            <section className="admin-card full">
              <div className="admin-card-head"><h2>Billing Dashboard</h2><span>Last 30 days</span></div>
              <div className="admin-metrics billing-metrics">
                <div className="admin-kpi"><span>Revenue</span><strong>Rs {Number(adminBilling?.revenue30d ?? 0).toLocaleString("en-IN")}</strong><em>Captured payments</em><CreditCard size={28} /></div>
                <div className="admin-kpi"><span>Subscriptions</span><strong>{adminBilling?.activeSubscriptions ?? 0}</strong><em>{adminBilling?.canceledSubscriptions ?? 0} canceled</em><Crown size={28} /></div>
                <div className="admin-kpi"><span>Payments</span><strong>{adminBilling?.payments30d ?? 0}</strong><em>Orders and captures</em><ClipboardList size={28} /></div>
                <div className="admin-kpi"><span>AI Cost</span><strong>${Number(adminBilling?.aiCost30d ?? 0).toFixed(4)}</strong><em>Tracked model cost</em><BrainCircuit size={28} /></div>
              </div>
              <div className="admin-dashboard-grid">
                <section className="admin-card">
                  <div className="admin-card-head"><h2>Revenue Dashboard</h2><span>By plan</span></div>
                  {(adminBilling?.revenueByPlan?.length ? adminBilling.revenueByPlan : []).map((item) => (
                    <div className="admin-table-row" key={item.plan ?? "plan"}><span>{item.plan ?? "Unknown"}</span><strong>Rs {Number(item.revenue ?? 0).toLocaleString("en-IN")}</strong></div>
                  ))}
                </section>
                <section className="admin-card">
                  <div className="admin-card-head"><h2>Subscription Dashboard</h2><span>Status</span></div>
                  <div className="admin-table-row"><span>Active</span><strong>{adminBilling?.activeSubscriptions ?? 0}</strong></div>
                  <div className="admin-table-row"><span>Canceled</span><strong>{adminBilling?.canceledSubscriptions ?? 0}</strong></div>
                </section>
                <section className="admin-card">
                  <div className="admin-card-head"><h2>Payment Dashboard</h2><span>By status</span></div>
                  {(adminBilling?.paymentsByStatus?.length ? adminBilling.paymentsByStatus : []).map((item) => (
                    <div className="admin-table-row" key={item.status ?? "status"}><span>{item.status ?? "Unknown"}</span><strong>{item.count ?? 0}</strong></div>
                  ))}
                </section>
                <section className="admin-card">
                  <div className="admin-card-head"><h2>AI Cost Dashboard</h2><span>30 days</span></div>
                  <div className="plan-donut"><strong>${Number(adminBilling?.aiCost30d ?? 0).toFixed(2)}</strong><span>Estimated spend</span></div>
                </section>
              </div>
            </section>
          ) : adminView === "feedback" ? (
            <section className="admin-card full">
              <div className="admin-card-head"><h2>Support Messages</h2><span>{metrics?.newFeedback ?? 0} new</span></div>
              <div className="admin-list">
                {adminData?.feedback.length ? adminData.feedback.map((item) => (
                  <article className="feedback-row" key={item.id}>
                    <div className="feedback-row-head"><strong>{item.category}</strong><span className={`status-pill status-${item.status.toLowerCase()}`}>{item.status}</span></div>
                    <p>{item.message}</p>
                    <div className="feedback-meta"><span>{item.userName || "Anonymous"} {item.userEmail ? `- ${item.userEmail}` : ""}</span><span>{item.rating}/5</span><span>{new Date(item.createdAt).toLocaleString()}</span></div>
                    <div className="feedback-actions">{["NEW", "REVIEWING", "RESOLVED"].map((status) => <button className="mini-action" disabled={item.status === status} key={status} onClick={() => changeFeedbackStatus(item.id, status)} type="button">{status}</button>)}</div>
                  </article>
                )) : <div className="empty-state">No feedback has been submitted yet.</div>}
              </div>
            </section>
          ) : (
            <section className="admin-card full">
              <div className="admin-card-head"><h2>{adminNav.find((item) => item.id === adminView)?.label} Management</h2><span>Operational workspace</span></div>
              <div className="admin-management-grid">
                {["Overview", "Records", "Permissions", "Activity Logs"].map((item) => (
                  <div className="management-tile" key={item}><strong>{item}</strong><span>Manage {adminView} {item.toLowerCase()} from this section.</span></div>
                ))}
              </div>
            </section>
          )}
        </section>
      </main>
    );
  }

  if (!isLoggedIn) {
    return (
      <main className={adminLoginMode ? "login-shell superadmin-login-shell" : "login-shell"}>
        <section className={adminLoginMode ? "login-hero superadmin-login-hero" : "login-hero"}>
          <div className="login-copy">
            <div className="brand">
              <span className="brand-mark">
                {adminLoginMode ? <Crown size={25} /> : <GraduationCap size={25} />}
              </span>
              JagdiSu
            </div>
            <h1 className="login-title">{adminLoginMode ? "Owner Access" : "Welcome Back!"}</h1>
            <span className="title-rule" />
            <p className="login-subtitle">
              {adminLoginMode ? "Manage JagdiSu users, feedback, and platform signals." : "Continue your learning journey with JagdiSu."}
            </p>
            <div className="login-metrics">
              <span>
                <strong>{adminLoginMode ? "Secure" : "Free"}</strong>
                {adminLoginMode ? "Access" : "Plan"}
              </span>
              <span>
                <strong>{adminLoginMode ? "Owner" : "Smart"}</strong>
                {adminLoginMode ? "Control" : "Quiz"}
              </span>
              <span>
                <strong>{adminLoginMode ? "Live" : "JEE"}</strong>
                {adminLoginMode ? "Dashboard" : "NEET/UPSC"}
              </span>
            </div>
          </div>

          <div className="auth-wrap panel panel-pad login-card">
            <span className="login-card-logo">
              <GraduationCap size={42} />
              <BookOpen size={32} />
            </span>
            <h2 className="auth-title">{adminLoginMode ? "Superadmin" : "JagdiSu"}</h2>
            <p className="subtitle">{adminLoginMode ? "Owner dashboard login" : "Login to your account"}</p>
            <span className="title-rule compact" />

            <div className="form-grid" style={{ gridTemplateColumns: "1fr" }}>
              <label className="field">
                <div className="input-icon">
                  <UserRound size={24} />
                  <input
                    className="input bare"
                    placeholder={adminLoginMode ? "Superadmin Email" : "Username or Email"}
                    value={adminLoginMode ? adminEmail : authIdentifier}
                    onChange={(event) => adminLoginMode ? setAdminEmail(event.target.value) : setAuthIdentifier(event.target.value)}
                  />
                </div>
              </label>
              <label className="field">
                <div className="input-icon">
                  <LockKeyhole size={23} />
                  <input
                    className="input bare"
                    type={authPasswordVisible ? "text" : "password"}
                    placeholder="Password"
                    value={adminLoginMode ? adminPassword : authPassword}
                    onChange={(event) => adminLoginMode ? setAdminPassword(event.target.value) : setAuthPassword(event.target.value)}
                  />
                  <button
                    className="password-toggle"
                    type="button"
                    onClick={() => setAuthPasswordVisible((visible) => !visible)}
                    aria-label={authPasswordVisible ? "Hide password" : "Show password"}
                  >
                    {authPasswordVisible ? <Eye size={22} /> : <EyeOff size={22} />}
                  </button>
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
              <button className="button primary" onClick={adminLoginMode ? loginAsSuperadmin : login} disabled={loading} type="button">
                {adminLoginMode ? <Crown size={23} /> : <GraduationCap size={23} />} {loading ? "Please wait..." : adminLoginMode ? "Open Dashboard" : "Login"}
              </button>
              {adminLoginMode ? null : (
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
              )}
              {adminLoginMode ? (
                <p className="quote">Default local admin: admin@jagdisu.local / 123456</p>
              ) : GOOGLE_LOGIN_ENABLED ? (
                <>
                  <div className="divider"><span>or</span></div>
                  {GOOGLE_CLIENT_ID ? (
                    <div className="google-login-slot" ref={googleButtonRef} />
                  ) : (
                    <button className="button google" onClick={() => loginWithGoogle()} disabled={loading} type="button">
                      <span className="google-mark">G</span> Login with Google
                    </button>
                  )}
                </>
              ) : null}
              <p className="quote">Learn Today, Achieve Tomorrow.</p>
            </div>
          </div>
        </section>
        <div className="site-help-widget">
          {helpMenuOpen ? (
            <div className="site-help-menu" role="menu" aria-label="Help options">
              <strong>Need help?</strong>
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  window.location.href = "/complaint";
                }}
              >
                <MessageCircle size={17} /> User complaint
              </button>
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setAdminLoginMode(true);
                  setHelpMenuOpen(false);
                  setError("");
                }}
              >
                <Crown size={17} /> Superadmin login
              </button>
            </div>
          ) : null}
          <button
            className="site-help-button"
            type="button"
            aria-expanded={helpMenuOpen}
            onClick={() => setHelpMenuOpen((value) => !value)}
          >
            Help
          </button>
        </div>
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
                      type={signupPasswordVisible ? "text" : "password"}
                      placeholder="Password"
                      value={signupPassword}
                      onChange={(event) => setSignupPassword(event.target.value)}
                    />
                    <button
                      className="password-toggle"
                      type="button"
                      onClick={() => setSignupPasswordVisible((visible) => !visible)}
                      aria-label={signupPasswordVisible ? "Hide password" : "Show password"}
                    >
                      {signupPasswordVisible ? <Eye size={21} /> : <EyeOff size={21} />}
                    </button>
                  </label>
                  <label className="signup-input">
                    <LockKeyhole size={22} />
                    <input
                      type={signupConfirmPasswordVisible ? "text" : "password"}
                      placeholder="Confirm Password"
                      value={signupConfirmPassword}
                      onChange={(event) => setSignupConfirmPassword(event.target.value)}
                    />
                    <button
                      className="password-toggle"
                      type="button"
                      onClick={() => setSignupConfirmPasswordVisible((visible) => !visible)}
                      aria-label={signupConfirmPasswordVisible ? "Hide password" : "Show password"}
                    >
                      {signupConfirmPasswordVisible ? <Eye size={21} /> : <EyeOff size={21} />}
                    </button>
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
  const totalQuizQuestions = targetQuestionCount || quiz?.questions.length || parseQuestionCountInput();
  const progress = quiz ? ((currentIndex + 1) / totalQuizQuestions) * 100 : 0;
  const isTakingQuiz = activeView === "quiz" && Boolean(quiz) && !showResult;

  return (
    <main className="app-shell">
      <section className="screen">
        <header className="topbar">
          <button className="brand brand-button" onClick={goHome} type="button" aria-label="Go to homepage">
            <span className="brand-mark">
              <GraduationCap size={24} />
            </span>
            JagdiSu
          </button>
          <div className="nav-actions">
            {(activeView !== "home" || Boolean(quiz) || showResult) ? (
              <button className="button ghost back-button" onClick={goBack} title="Back" type="button">
                <ArrowLeft size={18} /> Back
              </button>
            ) : null}
            <button className="nav-tab" onClick={() => setActiveView("community")} type="button">
              <MessageCircle size={16} /> Community
            </button>
            <button className="nav-tab" onClick={() => setActiveView("progress")} type="button">
              <BarChart3 size={16} /> Progress
            </button>
            <button className="nav-tab" onClick={() => setActiveView("feedback")} type="button">
              <ClipboardList size={16} /> Feedback
            </button>
            <button className="button ghost" onClick={() => setActiveView("subscription")} title="Subscription" type="button">
              <Crown size={18} />
              <span className="subscription-nav-badge">
                {subscriptionOverview?.planCode ?? plan}
                {subscriptionOverview?.status === "CANCELED" ? " · Canceled" : ""}
              </span>
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
                <p className="subtitle">Start an exam practice test, generate structured study notes, or evaluate a written answer.</p>
                <div className="student-usage-strip">
                  <div>
                    <span>Current Plan</span>
                    <strong>{subscriptionOverview?.planName ?? plan}</strong>
                  </div>
                  {(subscriptionOverview?.usage?.length ? subscriptionOverview.usage : [
                    { feature: "Quiz Usage", remaining: remainingFreeQuizzes, limit: FREE_DAILY_LIMIT },
                    { feature: "Notes Usage", remaining: 5, limit: 5 },
                    { feature: "AI Usage", remaining: 10000, limit: 10000 }
                  ]).slice(0, 3).map((item) => (
                    <div key={item.feature}>
                      <span>{item.feature.replace("_", " ")}</span>
                      <strong>{item.remaining.toLocaleString()} left</strong>
                    </div>
                  ))}
                </div>
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
                    <span>Create structured AI study notes with clear explanations, key points, and revision support.</span>
                    <em>Start studying</em>
                  </button>
                  <button className="choice-card" type="button" onClick={() => setActiveView("notes")}>
                    <span className="choice-icon"><ScanText size={26} /></span>
                    <span className="plan-kicker">Answer Checker</span>
                    <strong>Evaluate a Written Answer</strong>
                    <span>Upload the question and answer, review the extracted text, and receive exam-style marks with actionable feedback.</span>
                    <em>Review answer</em>
                  </button>
                  <button className="choice-card" type="button" onClick={() => setActiveView("notesLab")}>
                    <span className="choice-icon"><BrainCircuit size={26} /></span>
                    <span className="plan-kicker">Notes to Quiz</span>
                    <strong>Start Quiz From Notes</strong>
                    <span>Upload notes, choose question count and language, then start a quiz immediately.</span>
                    <em>Upload notes</em>
                  </button>
                  <button className="choice-card" type="button" onClick={() => setActiveView("community")}>
                    <span className="choice-icon"><MessageCircle size={26} /></span>
                    <span className="plan-kicker">Community</span>
                    <strong>Ask Doubts</strong>
                    <span>Post questions and answer doubts from other JagdiSu students.</span>
                    <em>Open community</em>
                  </button>
                </div>
              </div>
            ) : activeView === "topic" ? (
              <div className="topic-page">
                <span className="pill">
                  <BookOpen size={15} /> Topic Notes
                </span>
                <h1 className="section-title">Study a Topic</h1>
                <p className="subtitle">Enter a topic and choose a language. JagdiSu AI will generate structured notes for focused revision.</p>
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
                    <p>Designed for exam-focused depth, highlighted concepts, and practical revision cues.</p>
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
                  <ScanText size={15} /> Written Answer Evaluation
                </span>
                <h1 className="section-title">Evaluate a Written Answer</h1>
                <p className="subtitle">
                  Upload a PDF or image that contains both the question and the answer. JagdiSu will extract the text, let you review it, and then evaluate the response.
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
                      <strong>{notesFile ? notesFile.name : "Upload question and answer file"}</strong>
                      <span>A single PDF is recommended. PNG and JPG files are also supported when the page includes both the question and answer.</span>
                      <input
                        type="file"
                        accept="application/pdf,image/png,image/jpeg,image/jpg"
                        onChange={(event) => {
                          const file = event.target.files?.[0] ?? null;
                          setNotesFile(file);
                          setNotesResult(null);
                          setDemoAnswerVisible(false);
                          setNotesForm((current) => ({ ...current, questionPaperText: "", answerText: "" }));
                          void extractUploadedNotes(file);
                        }}
                      />
                    </label>
                    {notesExtracting ? (
                      <div className="batch-status">
                        <ScanText size={16} />
                        <span>Extracting the question and answer text...</span>
                      </div>
                    ) : null}
                    <label className="field">
                      <span className="label-row">Question Text</span>
                      <textarea
                        className="topic-input tall"
                        placeholder="The extracted question will appear here. You can edit it before evaluation."
                        value={notesForm.questionPaperText}
                        onChange={(event) => setNotesForm({ ...notesForm, questionPaperText: event.target.value })}
                      />
                    </label>
                    <label className="field">
                      <span className="label-row">Answer Text</span>
                      <textarea
                        className="topic-input tall answer-textarea"
                        placeholder="The extracted answer will appear here. You can edit it before evaluation."
                        value={notesForm.answerText}
                        onChange={(event) => setNotesForm({ ...notesForm, answerText: event.target.value })}
                      />
                    </label>
                    {error ? <div className="error">{error}</div> : null}
                    <button
                      className="button primary"
                      disabled={notesLoading || notesExtracting || !notesForm.examName.trim() || !notesForm.answerText.trim()}
                      onClick={submitNotesEvaluation}
                    >
                      <FileCheck size={18} /> {notesLoading ? "Evaluating Answer..." : "Evaluate Answer"}
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
                        <strong>Analyzing the submitted page</strong>
                        <p>JagdiSu is reviewing content accuracy, structure, clarity, and exam-style scoring.</p>
                      </div>
                    ) : notesResult ? (
                      <div className="evaluation-result">
                        <div
                          className="score-ring"
                          style={{
                            ["--score-progress" as string]: `${Math.max(
                              0,
                              Math.min(100, Math.round((notesResult.score / Math.max(1, notesResult.maxScore)) * 100))
                            )}%`
                          }}
                        >
                          <div>
                            <strong>{notesResult.score}</strong>
                            <span>/{notesResult.maxScore}</span>
                          </div>
                        </div>
                        <div>
                          <span className="plan-kicker">{notesResult.examName}</span>
                          <h2>Answer Feedback</h2>
                          <p>{notesResult.feedback}</p>
                          <div className="demo-answer-actions">
                            <button className="button secondary" type="button" onClick={() => setDemoAnswerVisible((value) => !value)}>
                              <FileCheck size={18} /> {demoAnswerVisible ? "Hide Demo Answer" : "Show Demo Answer"}
                            </button>
                            <button className="button ghost" type="button" onClick={downloadDemoAnswer}>
                              <Download size={18} /> Download PDF
                            </button>
                          </div>
                        </div>
                        {demoAnswerVisible ? (
                          <div className="evaluation-block demo-answer-block">
                            <div className="notes-head">
                              <h3>Corrected Demo Answer</h3>
                              <span className="trademark-line">JagdiSu TM</span>
                            </div>
                            <p>{notesResult.idealAnswer || "No demo answer was generated."}</p>
                          </div>
                        ) : null}
                        <div className="evaluation-block">
                          <h3>Extracted Answer</h3>
                          <p>{notesResult.extractedText || "No readable text returned."}</p>
                        </div>
                        <div className="evaluation-columns">
                          <div>
                            <h3>Weaknesses</h3>
                            {(notesResult.weaknesses ?? notesResult.mistakes).map((item) => (
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
                        <div className="evaluation-block">
                          <h3>Improvement Plan</h3>
                          {(notesResult.improvements ?? []).map((item) => (
                            <p key={item}>{item}</p>
                          ))}
                          {(!notesResult.improvements || notesResult.improvements.length === 0) ? (
                            <p>Revise weak points and rewrite the answer with clearer structure.</p>
                          ) : null}
                        </div>
                        <div className="evaluation-columns">
                          <div>
                            <h3>Mistakes</h3>
                            {notesResult.mistakes.map((item) => (
                              <p key={item}>{item}</p>
                            ))}
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="empty-state checker-empty">
                        <ScanText size={42} />
                        <strong>Upload an answer for evaluation</strong>
                        <span>Your score, strengths, weaknesses, mistakes, and improvement plan will appear here.</span>
                      </div>
                    )}
                  </section>
                </div>
              </div>
            ) : activeView === "notesLab" ? (
              <div className="notes-lab-page">
                <div className="simple-notes-quiz">
                  <div>
                    <span className="pill">
                      <BrainCircuit size={15} /> Notes Based Quiz
                    </span>
                    <h1 className="section-title">Upload Notes and Start Quiz</h1>
                    <p className="subtitle">
                      Bas notes upload karo, questions count aur language choose karo. Quiz turant start hoga; bade notes ke liye AI pehle chhota part read karega aur baaki questions background me add hote rahenge.
                    </p>
                  </div>
                  <label
                    className="mega-file-drop"
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={(event) => {
                      event.preventDefault();
                      analyzeNotesLabFiles(event.dataTransfer.files);
                    }}
                  >
                    <Upload size={46} />
                    <strong>{notesLabUploads.length ? `${notesLabUploads.length} notes selected` : "Upload notes"}</strong>
                    <span>PDF, image, ya handwritten notes upload karo. Questions sirf uploaded notes ke source text se banenge.</span>
                    <input
                      multiple
                      type="file"
                      accept=".pdf,image/png,image/jpeg,image/jpg,image/webp"
                      onChange={(event) => analyzeNotesLabFiles(event.target.files ?? [])}
                    />
                  </label>

                  {notesLabUploads.length ? (
                    <div className="simple-upload-list">
                      {notesLabUploads.map((file) => (
                        <div className="upload-row" key={file.id}>
                          <div>
                            <strong>{file.name}</strong>
                            <span>{file.pages} pages | {file.language} | {file.ocrRequired ? "OCR will run" : "Text readable"}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : null}

                  <div className="simple-notes-form">
                    <label className="field">
                      <span className="label-row">Questions</span>
                      <select
                        className="select"
                        value={notesLabQuestionCount}
                        onChange={(event) => setNotesLabQuestionCount(Number(event.target.value))}
                      >
                        {[10, 20, 50, 100].map((count) => (
                          <option key={count} value={count}>{count}</option>
                        ))}
                      </select>
                    </label>
                    <label className="field">
                      <span className="label-row">Language</span>
                      <select
                        className="select"
                        value={notesLabLanguage}
                        onChange={(event) => setNotesLabLanguage(event.target.value)}
                      >
                        <option>English</option>
                        <option>Hindi</option>
                        <option>Hinglish</option>
                      </select>
                    </label>
                    {error ? <div className="error field full">{error}</div> : null}
                    <button
                      className="button primary field full"
                      disabled={loading || notesLabUploads.length === 0 || notesLabFiles.length === 0}
                      onClick={startNotesBasedQuiz}
                      type="button"
                    >
                      <Sparkles size={18} /> {loading ? "Starting Quiz..." : "Start Quiz"}
                    </button>
                  </div>

                  <div className="simple-note-behavior">
                    <BrainCircuit size={22} />
                    <span>Large notes me first part se quiz start hoga. Baaki questions background me add hote rahenge.</span>
                  </div>
                </div>
              </div>
            ) : activeView === "community" ? (
              <div className="community-page">
                <section className="community-chat-shell">
                  <div className="community-chat-header">
                    <div>
                      <span className="pill">
                        <MessageCircle size={15} /> JagdiSu Doubt Group
                      </span>
                      <h1>Community Chat</h1>
                      <p>{communityQuestions.length} questions | answers ranked by likes</p>
                    </div>
                    <button className="button ghost" type="button" onClick={refreshCommunity} disabled={communityLoading}>
                      <RotateCcw size={18} /> Refresh
                    </button>
                  </div>

                  <div className="community-chat-feed">
                    {communityQuestions.length === 0 ? (
                      <div className="empty-state">
                        <MessageCircle size={38} />
                        <strong>No questions yet</strong>
                        <span>Group me first question bhejo.</span>
                      </div>
                    ) : (
                      communityQuestions.map((question) => {
                        const sortedAnswers = [...question.answers].sort(
                          (left, right) => right.upvotes - left.upvotes || new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
                        );
                        const topAnswer = sortedAnswers[0];

                        return (
                          <article className="chat-question-block" key={question.id}>
                            <div className="chat-bubble question-bubble">
                              <div className="bubble-meta">
                                <strong>{question.userName}</strong>
                                <span>{question.examType} | {question.subject}{question.topic ? ` | ${question.topic}` : ""}</span>
                              </div>
                              <h2>{question.title}</h2>
                              <p>{question.body}</p>
                              <span className="bubble-time">{new Date(question.createdAt).toLocaleString()}</span>
                            </div>

                            {topAnswer ? (
                              <div className="top-answer-card">
                                <span className="plan-kicker">Top answer near question</span>
                                <div className="community-answer compact">
                                  <div>
                                    <strong>{topAnswer.userName}</strong>
                                    <button className="like-button" type="button" onClick={() => likeAnswer(question.id, topAnswer.id)}>
                                      Like {topAnswer.upvotes}
                                    </button>
                                  </div>
                                  <p>{topAnswer.body}</p>
                                </div>
                              </div>
                            ) : null}

                            <div className="answer-thread">
                              {sortedAnswers.length === 0 ? (
                                <div className="answer-empty">Abhi answer nahi hai. Solve karke help karo.</div>
                              ) : (
                                sortedAnswers.map((answer) => (
                                  <div className="chat-bubble answer-bubble" key={answer.id}>
                                    <div className="bubble-meta">
                                      <strong>{answer.userName}</strong>
                                      <button className="like-button" type="button" onClick={() => likeAnswer(question.id, answer.id)}>
                                        Like {answer.upvotes}
                                      </button>
                                    </div>
                                    <p>{answer.body}</p>
                                    <span className="bubble-time">{new Date(answer.createdAt).toLocaleString()}</span>
                                  </div>
                                ))
                              )}
                            </div>

                            <div className="chat-reply-bar">
                              <textarea
                                className="topic-input"
                                placeholder="Reply / solve this question..."
                                value={communityAnswers[question.id] ?? ""}
                                onChange={(event) => setCommunityAnswers({ ...communityAnswers, [question.id]: event.target.value })}
                              />
                              <button className="button secondary" type="button" onClick={() => postCommunityAnswer(question.id)} disabled={communityLoading}>
                                <Send size={18} /> Send
                              </button>
                            </div>
                          </article>
                        );
                      })
                    )}
                  </div>

                  <div className="community-chat-composer">
                    <h2>Send a Question</h2>
                  <div className="form-grid">
                    <label className="field full">
                      <span className="label-row">Question Title</span>
                      <input
                        className="input"
                        placeholder="e.g., Article 32 ko heart and soul kyu kaha jata hai?"
                        value={communityQuestionForm.title}
                        onChange={(event) => setCommunityQuestionForm({ ...communityQuestionForm, title: event.target.value })}
                      />
                    </label>
                    <label className="field">
                      <span className="label-row">Exam</span>
                      <select
                        className="select"
                        value={communityQuestionForm.examType}
                        onChange={(event) => setCommunityQuestionForm({ ...communityQuestionForm, examType: event.target.value })}
                      >
                        {["UPSC", "BPSC", "SSC", "Railway", "Bank PO", "JEE", "NEET", "General"].map((exam) => (
                          <option key={exam}>{exam}</option>
                        ))}
                      </select>
                    </label>
                    <label className="field">
                      <span className="label-row">Subject</span>
                      <input
                        className="input"
                        placeholder="e.g., Polity"
                        value={communityQuestionForm.subject}
                        onChange={(event) => setCommunityQuestionForm({ ...communityQuestionForm, subject: event.target.value })}
                      />
                    </label>
                    <label className="field full">
                      <span className="label-row">Topic</span>
                      <input
                        className="input"
                        placeholder="e.g., Fundamental Rights"
                        value={communityQuestionForm.topic}
                        onChange={(event) => setCommunityQuestionForm({ ...communityQuestionForm, topic: event.target.value })}
                      />
                    </label>
                    <label className="field full">
                      <span className="label-row">Question Details</span>
                      <textarea
                        className="topic-input tall"
                        placeholder="Apna doubt detail me likho. Formula, options, ya confusion bhi add kar sakte ho."
                        value={communityQuestionForm.body}
                        onChange={(event) => setCommunityQuestionForm({ ...communityQuestionForm, body: event.target.value })}
                      />
                    </label>
                    {communityNotice ? <div className="notice field full">{communityNotice}</div> : null}
                    <button className="button primary field full" type="button" onClick={postCommunityQuestion} disabled={communityLoading}>
                      <Send size={18} /> {communityLoading ? "Sending..." : "Send Question"}
                    </button>
                  </div>
                  </div>
                </section>
              </div>
            ) : activeView === "feedback" ? (
              <div className="feedback-page">
                <span className="pill">
                  <ClipboardList size={15} /> Product Feedback
                </span>
                <h1 className="section-title">Send Feedback</h1>
                <p className="subtitle">
                  Tell us what is working, what feels confusing, or what should be improved. Your message goes directly to the superadmin dashboard.
                </p>
                <div className="feedback-form">
                  <label className="field">
                    <span className="label-row">Category</span>
                    <select
                      className="select"
                      value={feedbackForm.category}
                      onChange={(event) => setFeedbackForm({ ...feedbackForm, category: event.target.value })}
                    >
                      <option>General Feedback</option>
                      <option>Bug Report</option>
                      <option>Feature Request</option>
                      <option>Quiz Quality</option>
                      <option>Payment or Subscription</option>
                    </select>
                  </label>
                  <label className="field">
                    <span className="label-row">Rating</span>
                    <select
                      className="select"
                      value={feedbackForm.rating}
                      onChange={(event) => setFeedbackForm({ ...feedbackForm, rating: Number(event.target.value) })}
                    >
                      <option value={5}>5 - Excellent</option>
                      <option value={4}>4 - Good</option>
                      <option value={3}>3 - Average</option>
                      <option value={2}>2 - Needs work</option>
                      <option value={1}>1 - Poor</option>
                    </select>
                  </label>
                  <label className="field full">
                    <span className="label-row">Message</span>
                    <textarea
                      className="topic-input tall"
                      placeholder="Write the issue, suggestion, or improvement you want the owner to review."
                      value={feedbackForm.message}
                      onChange={(event) => setFeedbackForm({ ...feedbackForm, message: event.target.value })}
                    />
                  </label>
                  {feedbackNotice ? <div className="notice field full">{feedbackNotice}</div> : null}
                  <button className="button primary field full" type="button" disabled={feedbackLoading} onClick={sendFeedback}>
                    <ClipboardList size={18} /> {feedbackLoading ? "Submitting..." : "Submit Feedback"}
                  </button>
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
                <h1 className="section-title">Subscription</h1>
                <p className="subtitle">
                  Manage your current plan, billing, usage, and renewal.
                </p>
                {subscriptionNotice ? <div className="notice">{subscriptionNotice}</div> : null}
                <section className="subscription-current-card">
                  <div>
                    <span className="plan-kicker">Current Plan</span>
                    <h2>{subscriptionOverview?.planName ?? plan}</h2>
                    <span className={`status-badge ${(subscriptionOverview?.status ?? "ACTIVE").toLowerCase()}`}>
                      {subscriptionOverview?.status ?? "ACTIVE"}
                    </span>
                  </div>
                  <div className="subscription-renewal">
                    <span>Renewal Date</span>
                    <strong>{formatBillingDate(subscriptionOverview?.renewalDate)}</strong>
                    <em>{subscriptionOverview?.monthlyPriceInr ? `Rs ${subscriptionOverview.monthlyPriceInr}/month` : "Free access"}</em>
                  </div>
                  <button className="button ghost" type="button" onClick={() => currentUser?.accessToken && refreshSubscription(currentUser.accessToken)}>
                    <RotateCcw size={18} /> Refresh
                  </button>
                </section>

                <section className="usage-dashboard">
                  <div className="admin-card-head"><h2>Usage Dashboard</h2><span>Live limits</span></div>
                  <div className="usage-grid">
                    {(subscriptionOverview?.usage?.length ? subscriptionOverview.usage : [
                      { feature: "QUIZ", used: dailyUsage, limit: FREE_DAILY_LIMIT, remaining: remainingFreeQuizzes, period: "DAILY" },
                      { feature: "NOTES", used: 0, limit: 5, remaining: 5, period: "DAILY" },
                      { feature: "AI_TOKENS", used: 0, limit: 10000, remaining: 10000, period: "MONTHLY" }
                    ]).map((item) => (
                      <div className="usage-card" key={item.feature}>
                        <span>{item.feature.replace("_", " ")}</span>
                        <strong>{item.remaining.toLocaleString()} left</strong>
                        <div className="usage-meter">
                          <span style={{ width: `${item.limit ? Math.min(100, (item.used / item.limit) * 100) : 0}%` }} />
                        </div>
                        <em>{item.used.toLocaleString()} / {item.limit.toLocaleString()} {item.period.toLowerCase()}</em>
                      </div>
                    ))}
                  </div>
                </section>

                <div className="plan-grid">
                  <button
                    className={`plan-box plan-select ${plan === "FREE" ? "active" : ""}`}
                    onClick={() => {
                      setSelectedSubscriptionPlan("FREE");
                      setSubscriptionNotice("Free plan paid checkout require nahi karta. Paid plan cancel karne ke liye Cancel Subscription use karo.");
                    }}
                    type="button"
                  >
                    <span className="plan-kicker">Limited</span>
                    <h2>Free</h2>
                    <strong>Rs 0</strong>
                    <p className="subtitle">Limited test generations for daily practice.</p>
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
                    <span className="plan-action">{plan === "ADVANCED" ? "Downgrade to Pro" : plan === "PRO" ? "Active" : "Upgrade to Pro"}</span>
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
                    <span className="plan-action">{plan === "ADVANCED" ? "Active" : "Upgrade to Advanced"}</span>
                  </button>
                </div>
                {selectedSubscriptionPlan === "FREE" ? null : (
                <section className="subscription-action-panel">
                  <div>
                    <span className="plan-kicker">Razorpay Checkout</span>
                    <h2>{selectedPaidPlan === "ADVANCED" ? "Advanced" : "Pro"} - Rs {selectedPlanPrice}/month</h2>
                    <p className="subtitle">{plan === "ADVANCED" && selectedPaidPlan === "PRO" ? "Downgrade" : plan === "PRO" && selectedPaidPlan === "ADVANCED" ? "Upgrade" : "Activate"} flow payment verification ke baad backend subscription update karega.</p>
                  </div>
                  <button className="button primary" disabled={subscriptionLoading || plan === selectedPaidPlan} onClick={() => startPlanCheckout(selectedPaidPlan)}>
                    <CreditCard size={18} /> {subscriptionLoading ? "Processing..." : plan === selectedPaidPlan ? "Current Plan" : plan === "ADVANCED" && selectedPaidPlan === "PRO" ? "Downgrade Plan" : "Upgrade Plan"}
                  </button>
                </section>
                )}
                <section className="subscription-actions-row">
                  {subscriptionOverview?.status === "CANCELED" ? (
                    <button className="button primary" type="button" disabled={subscriptionLoading} onClick={reactivateCurrentSubscription}>
                      <RotateCcw size={18} /> Reactivate Subscription
                    </button>
                  ) : plan !== "FREE" ? (
                    <button className="button secondary" type="button" disabled={subscriptionLoading} onClick={cancelCurrentSubscription}>
                      <X size={18} /> Cancel Subscription
                    </button>
                  ) : null}
                </section>
                <section className="billing-history">
                  <div className="admin-card-head"><h2>Billing History</h2><span>{subscriptionOverview?.invoices?.length ?? 0} invoices</span></div>
                  <div className="billing-table">
                    {(subscriptionOverview?.invoices?.length ? subscriptionOverview.invoices : []).map((invoice) => (
                      <div className="billing-row" key={invoice.invoiceNumber}>
                        <span>{invoice.invoiceNumber}</span>
                        <strong>{invoice.currency} {Number(invoice.amount).toLocaleString("en-IN")}</strong>
                        <em>{formatBillingDate(invoice.createdAt)} · {invoice.status}</em>
                        <button className="mini-action" type="button" onClick={() => downloadBillingInvoice(invoice.invoiceNumber)}>
                          <Download size={16} /> Invoice
                        </button>
                      </div>
                    ))}
                    {!subscriptionOverview?.invoices?.length ? (
                      <div className="empty-state">
                        <CreditCard size={34} />
                        <strong>No invoices yet</strong>
                        <span>Your paid invoices will appear after successful payment.</span>
                      </div>
                    ) : null}
                  </div>
                </section>
                <button className="button ghost" onClick={() => setActiveView("quiz")}>
                  Back to Quiz
                </button>
              </div>
            ) : !quiz ? (
              <>
                <div className="quiz-mode-tabs" role="tablist" aria-label="Test mode">
                  <button
                    className={quizMode === "customize" ? "active" : ""}
                    onClick={() => {
                      setQuizMode("customize");
                      setError("");
                    }}
                    type="button"
                  >
                    Customize
                  </button>
                  <button
                    className={quizMode === "full" ? "active" : ""}
                    onClick={() => {
                      setQuizMode("full");
                      setError("");
                    }}
                    type="button"
                  >
                    Full Test
                  </button>
                </div>
                {quizMode === "customize" ? (
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
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]*"
                      placeholder="e.g., 20"
                      value={questionCountInput}
                      onChange={(event) => {
                        const value = event.target.value.replace(/\D/g, "");
                        setQuestionCountInput(value);
                        setForm({ ...form, numberOfQuestions: Number.parseInt(value, 10) || 0 });
                      }}
                      onBlur={() => {
                        const normalized = parseQuestionCountInput();
                        if (normalized) {
                          setQuestionCountInput(String(normalized));
                          setForm({ ...form, numberOfQuestions: normalized });
                        }
                      }}
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
                      disabled={loading || !form.subject || !form.examName || !questionCountInput}
                      onClick={generateQuiz}
                    >
                      <Sparkles size={18} /> {loading ? "Generating..." : "Generate Quiz"}
                    </button>
                  </div>
                </div>
                ) : (
                <div className="form-grid">
                  <label className="field full">
                    <span className="label-row">Exam Name</span>
                    <input
                      className="input"
                      placeholder="e.g., UPSC CSE, SSC CGL, NEET, JEE Main, GATE"
                      value={fullTestForm.examName}
                      onChange={(event) => setFullTestForm({ ...fullTestForm, examName: event.target.value })}
                    />
                  </label>
                  <label className="field">
                    <span className="label-row">Language</span>
                    <select
                      className="select"
                      value={fullTestForm.language}
                      onChange={(event) => setFullTestForm({ ...fullTestForm, language: event.target.value })}
                    >
                      <option>English</option>
                      <option>Hindi</option>
                      <option>Hinglish</option>
                    </select>
                  </label>
                  <div className="full-test-preview">
                    <div className="stat">
                      <span>Questions</span>
                      <strong>{fullTestPreview.totalQuestions}</strong>
                    </div>
                    <div className="stat">
                      <span>Duration</span>
                      <strong>{fullTestPreview.durationMinutes} min</strong>
                    </div>
                    <div className="stat">
                      <span>Negative</span>
                      <strong>{fullTestPreview.negativeMarking}</strong>
                    </div>
                  </div>
                  {error ? <div className="error field full">{error}</div> : null}
                  <div className="field full">
                    <button
                      className="button primary"
                      disabled={loading || !fullTestForm.examName.trim()}
                      onClick={generateFullTest}
                    >
                      <Sparkles size={18} /> {loading ? "Generating Full Test..." : "Start Full Test"}
                    </button>
                  </div>
                </div>
                )}
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
                {form.fullTestMode && fullTestAnalysis ? (
                  <section className="analysis-panel">
                    <div className="analysis-head">
                      <div>
                        <span className="pill">AI Analysis</span>
                        <h2>Full Test Intelligence</h2>
                      </div>
                      <div className="rank-badge">
                        <span>Rank Prediction</span>
                        <strong>{fullTestAnalysis.rankPrediction}</strong>
                      </div>
                    </div>
                    <div className="analysis-metrics">
                      <div>
                        <span>Accuracy %</span>
                        <strong>{fullTestAnalysis.accuracy}%</strong>
                      </div>
                      <div>
                        <span>Time Use</span>
                        <strong>{fullTestAnalysis.timeUsePercent}%</strong>
                      </div>
                      <div>
                        <span>Guessing Mistakes</span>
                        <strong>{fullTestAnalysis.guessingMistakes}</strong>
                      </div>
                      <div>
                        <span>Silly Mistakes</span>
                        <strong>{fullTestAnalysis.sillyMistakes}</strong>
                      </div>
                    </div>
                    <div className="analysis-columns">
                      <div>
                        <h3>Weak Topics</h3>
                        <div className="topic-list">
                          {(fullTestAnalysis.weakTopics.length ? fullTestAnalysis.weakTopics : [{ name: "No clear weak topic", accuracy: fullTestAnalysis.accuracy, wrong: result.wrong, unattempted: result.unattempted, total: quiz.questions.length, correct: result.correct }]).map((topic) => (
                            <div className="topic-row" key={topic.name}>
                              <span>{topic.name}</span>
                              <strong>{topic.accuracy}%</strong>
                            </div>
                          ))}
                        </div>
                      </div>
                      <div>
                        <h3>Strong Areas</h3>
                        <div className="topic-list">
                          {(fullTestAnalysis.strongTopics.length ? fullTestAnalysis.strongTopics : [{ name: "Build consistency first", accuracy: fullTestAnalysis.accuracy, wrong: result.wrong, unattempted: result.unattempted, total: quiz.questions.length, correct: result.correct }]).map((topic) => (
                            <div className="topic-row strong" key={topic.name}>
                              <span>{topic.name}</span>
                              <strong>{topic.accuracy}%</strong>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                    <div className="time-advice">
                      <BarChart3 size={20} />
                      <span>{fullTestAnalysis.timeManagement}</span>
                    </div>
                    <div className="study-plan">
                      <h3>Next Study Plan</h3>
                      {fullTestAnalysis.studyPlan.map((item, index) => (
                        <div className="plan-step" key={item}>
                          <span>{index + 1}</span>
                          <p>{item}</p>
                        </div>
                      ))}
                    </div>
                  </section>
                ) : null}
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
                    className="button primary"
                    onClick={openAnswerReview}
                  >
                    <FileCheck size={18} /> Check Answers
                  </button>
                  <button
                    className="button ghost"
                    style={{ width: "auto" }}
                    onClick={() => {
                      setQuiz(null);
                      setReviewAnswersMode(false);
                      setTargetQuestionCount(0);
                      setPrefetchNotice("");
                      setTimedTestActive(false);
                      setTestDurationSeconds(0);
                      setTimeRemainingSeconds(0);
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
                      {reviewAnswersMode ? "Check Answers" : "Question"} {currentIndex + 1} of {totalQuizQuestions}
                    </h1>
                  </div>
                  <div className="question-head-actions">
                    {testDurationSeconds > 0 ? (
                      <div className={`test-timer ${timeRemainingSeconds <= 300 ? "urgent" : ""}`}>
                        <span>Time Left</span>
                        <strong>{formatTimer(timeRemainingSeconds)}</strong>
                      </div>
                    ) : null}
                    <button
                      className="button ghost"
                      onClick={() => {
                        setQuiz(null);
                        setReviewAnswersMode(false);
                        setTargetQuestionCount(0);
                        setPrefetchNotice("");
                        setTimedTestActive(false);
                        setTestDurationSeconds(0);
                        setTimeRemainingSeconds(0);
                      }}
                      title="New quiz"
                    >
                      <RotateCcw size={18} />
                    </button>
                  </div>
                </div>
                <div className="progress">
                  <span style={{ width: `${progress}%` }} />
                </div>
                <section className="panel panel-pad question-card">
                  <p className="question-text">{question?.question}</p>
                  <div className="options">
                    {question?.options.map((option, index) => {
                      const isCorrect = index === question.correctAnswerIndex;
                      const isSelected = answers[question.id] === index;
                      return (
                        <button
                          key={option}
                          className={getOptionClass(question, index)}
                          onClick={() => {
                            if (!reviewAnswersMode) {
                              setAnswers({ ...answers, [question.id]: index });
                            }
                          }}
                          disabled={reviewAnswersMode}
                        >
                          <span>{option}</span>
                          {reviewAnswersMode && isCorrect ? <strong>Right answer</strong> : null}
                          {reviewAnswersMode && isSelected && !isCorrect ? <strong>Your answer</strong> : null}
                        </button>
                      );
                    })}
                  </div>
                  {reviewAnswersMode && question ? (
                    <div className="answer-review-panel">
                      <div>
                        <span className="pill">Deep Explanation</span>
                        <h2>{buildDeepExplanation(question).concept}</h2>
                      </div>
                      <p>{buildDeepExplanation(question).explanation}</p>
                      <div className="memory-trick">
                        <Lightbulb size={19} />
                        <span>{buildDeepExplanation(question).memoryTrick}</span>
                      </div>
                    </div>
                  ) : form.hintsEnabled && question ? (
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
                  <button
                    className="button primary"
                    style={{ width: "auto" }}
                    disabled={currentIndex === quiz.questions.length - 1 && quiz.questions.length < totalQuizQuestions}
                    onClick={() => {
                      if (reviewAnswersMode) {
                        if (currentIndex === quiz.questions.length - 1) {
                          setReviewAnswersMode(false);
                          setShowResult(true);
                        } else {
                          setCurrentIndex((value) => Math.min(quiz.questions.length - 1, value + 1));
                        }
                        setShowHint(false);
                        return;
                      }
                      const hasLoadedAllQuestions = quiz.questions.length >= totalQuizQuestions;
                      if (currentIndex === quiz.questions.length - 1 && hasLoadedAllQuestions) {
                        completeQuiz();
                      } else {
                        setCurrentIndex((value) => Math.min(quiz.questions.length - 1, value + 1));
                        setShowHint(false);
                      }
                    }}
                  >
                    {reviewAnswersMode && currentIndex === quiz.questions.length - 1
                      ? "Back to Report"
                      : reviewAnswersMode
                        ? "Next Answer"
                        : currentIndex === quiz.questions.length - 1 && quiz.questions.length >= totalQuizQuestions
                      ? "Submit Test"
                      : currentIndex === quiz.questions.length - 1 && form.examName !== "Notes Based Quiz"
                        ? "Loading Next..."
                        : "Next"}
                  </button>
                </div>
                {form.examName !== "Notes Based Quiz" && ((currentIndex === quiz.questions.length - 1 && quiz.questions.length < totalQuizQuestions) || prefetchNotice) ? (
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
