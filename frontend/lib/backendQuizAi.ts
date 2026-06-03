const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:2000/api";

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

export type QuizResponse = {
  quizId: string;
  examPatternSummary: string;
  difficulty: string;
  questions: Array<{
    id: string;
    question: string;
    options: string[];
    correctAnswerIndex: number;
    explanation: string;
    difficulty: string;
    subject?: string;
    topic?: string;
  }>;
};

export async function generateQuizWithBackend(request: QuizRequest): Promise<QuizResponse> {
  const response = await fetch(`${API_BASE_URL}/quizzes/generate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function generateNotesQuizWithBackend(request: {
  files: File[];
  language: string;
  numberOfQuestions: number;
  totalQuestions: number;
  batchNumber: number;
  previousQuestionSummaries?: string[];
}): Promise<QuizResponse> {
  const body = new FormData();
  body.append("language", request.language);
  body.append("numberOfQuestions", String(request.numberOfQuestions));
  body.append("totalQuestions", String(request.totalQuestions));
  body.append("batchNumber", String(request.batchNumber));
  request.previousQuestionSummaries?.forEach((summary) => body.append("previousQuestionSummaries", summary));
  request.files.forEach((file) => body.append("files", file));

  const response = await fetch(`${API_BASE_URL}/notes/quiz`, {
    method: "POST",
    body
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export type SubscriptionResponse = {
  plan: string;
  dailyQuizLimit: number;
  monthlyPriceInr: number;
  paymentRequired: boolean;
  note: string;
};

export type SubscriptionUsage = {
  feature: string;
  used: number;
  limit: number;
  remaining: number;
  period: string;
};

export type SubscriptionInvoice = {
  invoiceNumber: string;
  amount: number;
  currency: string;
  status: string;
  createdAt: string;
};

export type SubscriptionOverview = {
  planCode: string;
  planName: string;
  status: string;
  startsAt?: string | null;
  endsAt?: string | null;
  renewalDate?: string | null;
  monthlyPriceInr: number;
  usage: SubscriptionUsage[];
  entitlements: Array<{ feature: string; limit: number; period: string }>;
  invoices: SubscriptionInvoice[];
};

export type RazorpayOrderResponse = {
  paymentId: number;
  razorpayOrderId: string;
  keyId: string;
  amountPaise: number;
  currency: string;
  status: string;
  planCode: "PRO" | "ADVANCED";
};

export type SubscriptionActionResponse = {
  status: string;
  planCode: string;
  endsAt?: string | null;
  message: string;
};

export async function loadSubscriptionOverview(accessToken: string): Promise<SubscriptionOverview> {
  const response = await fetch(`${API_BASE_URL}/subscription/me`, {
    headers: authHeaders(accessToken)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function createRazorpayOrder(accessToken: string, planCode: "PRO" | "ADVANCED"): Promise<RazorpayOrderResponse> {
  const response = await fetch(`${API_BASE_URL}/subscription/subscribe`, {
    method: "POST",
    headers: authJsonHeaders(accessToken),
    body: JSON.stringify({ planCode })
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function verifyRazorpayPayment(
  accessToken: string,
  request: { razorpayOrderId: string; razorpayPaymentId: string; razorpaySignature: string }
): Promise<SubscriptionActionResponse> {
  const response = await fetch(`${API_BASE_URL}/payments/razorpay/activate`, {
    method: "POST",
    headers: authJsonHeaders(accessToken),
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function cancelSubscription(accessToken: string): Promise<SubscriptionActionResponse> {
  const response = await fetch(`${API_BASE_URL}/subscription/cancel`, {
    method: "POST",
    headers: authHeaders(accessToken)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function reactivateSubscription(accessToken: string): Promise<SubscriptionActionResponse> {
  const response = await fetch(`${API_BASE_URL}/subscription/reactivate`, {
    method: "POST",
    headers: authHeaders(accessToken)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function downloadInvoice(accessToken: string, invoiceNumber: string): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}/payments/invoices/${encodeURIComponent(invoiceNumber)}/download`, {
    headers: authHeaders(accessToken)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.blob();
}

export async function subscribeToPlan(plan: "PRO" | "ADVANCED", paymentReference: string): Promise<SubscriptionResponse> {
  void paymentReference;
  return {
    plan,
    dailyQuizLimit: 0,
    monthlyPriceInr: plan === "ADVANCED" ? 49 : 20,
    paymentRequired: true,
    note: "Use createRazorpayOrder and verifyRazorpayPayment for production subscriptions."
  };
}

export type TopicNotesResponse = {
  topic: string;
  language: string;
  notes: string;
};

export async function generateTopicNotes(request: { topic: string; language: string }): Promise<TopicNotesResponse> {
  const response = await fetch(`${API_BASE_URL}/topics/notes`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export type NotesEvaluationResponse = {
  examName: string;
  extractedText: string;
  mistakes: string[];
  strengths: string[];
  weaknesses: string[];
  improvements: string[];
  score: number;
  maxScore: number;
  feedback: string;
  idealAnswer: string;
};

export type FeedbackRequest = {
  userId?: number | null;
  userName?: string | null;
  userEmail?: string | null;
  category: string;
  rating: number;
  message: string;
};

export async function submitFeedback(request: FeedbackRequest): Promise<{ message: string }> {
  const response = await fetch(`${API_BASE_URL}/feedback`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export type AdminDashboardResponse = {
  metrics: {
    totalUsers: number;
    freeUsers: number;
    proUsers: number;
    advancedUsers: number;
    totalFeedback: number;
    newFeedback: number;
    averageRating: number;
  };
  recentUsers: Array<{
    id: number;
    name: string;
    email: string;
    plan: string;
    createdAt: string;
  }>;
  feedback: Array<{
    id: number;
    userName?: string | null;
    userEmail?: string | null;
    category: string;
    rating: number;
    message: string;
    status: string;
    createdAt: string;
  }>;
};

export type AdminBillingSummary = {
  revenue30d: number;
  activeSubscriptions: number;
  canceledSubscriptions: number;
  payments30d: number;
  aiCost30d: number;
  revenueByPlan: Array<{ plan?: string; revenue?: number }>;
  paymentsByStatus: Array<{ status?: string; count?: number; amount?: number }>;
};

export async function loginSuperadmin(email: string, password: string): Promise<{ role: string; email: string; token: string }> {
  const response = await fetch(`${API_BASE_URL}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password })
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function loadAdminDashboard(token: string): Promise<AdminDashboardResponse> {
  const response = await fetch(`${API_BASE_URL}/admin/dashboard`, {
    headers: { "X-Admin-Token": token }
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function loadAdminBillingSummary(token: string): Promise<AdminBillingSummary> {
  const response = await fetch(`${API_BASE_URL}/admin/billing-summary`, {
    headers: { "X-Admin-Token": token }
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function updateFeedbackStatus(token: string, id: number, status: string): Promise<{ id: number; status: string }> {
  const response = await fetch(`${API_BASE_URL}/admin/feedback/${id}/status`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Admin-Token": token
    },
    body: JSON.stringify({ status })
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export type CommunityAnswer = {
  id: number;
  questionId: number;
  userId?: number | null;
  userName: string;
  body: string;
  upvotes: number;
  createdAt: string;
};

export type CommunityQuestion = {
  id: number;
  userId?: number | null;
  userName: string;
  title: string;
  body: string;
  examType: string;
  subject: string;
  topic?: string | null;
  createdAt: string;
  answerCount: number;
  answers: CommunityAnswer[];
};

export async function loadCommunityQuestions(): Promise<CommunityQuestion[]> {
  const response = await fetch(`${API_BASE_URL}/community/questions`);

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function createCommunityQuestion(request: {
  userId?: number | null;
  userName?: string | null;
  title: string;
  body: string;
  examType: string;
  subject: string;
  topic?: string;
}): Promise<CommunityQuestion> {
  const response = await fetch(`${API_BASE_URL}/community/questions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function createCommunityAnswer(
  questionId: number,
  request: { userId?: number | null; userName?: string | null; body: string }
): Promise<CommunityAnswer> {
  const response = await fetch(`${API_BASE_URL}/community/questions/${questionId}/answers`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function likeCommunityAnswer(answerId: number): Promise<CommunityAnswer> {
  const response = await fetch(`${API_BASE_URL}/community/answers/${answerId}/like`, {
    method: "POST"
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export type NotesExtractionResponse = {
  questionText: string;
  answerText: string;
};

export async function extractHandwrittenNotes(request: {
  file?: File | null;
  questionFile?: File | null;
  answerFile?: File | null;
  language: string;
}): Promise<NotesExtractionResponse> {
  const body = new FormData();
  body.append("language", request.language);
  if (request.file) {
    body.append("file", request.file);
  }
  if (request.questionFile) {
    body.append("questionFile", request.questionFile);
  }
  if (request.answerFile) {
    body.append("answerFile", request.answerFile);
  }

  const response = await fetch(`${API_BASE_URL}/notes/extract`, {
    method: "POST",
    body
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

export async function evaluateHandwrittenNotes(request: {
  examName: string;
  questionPaperText: string;
  answerText: string;
  language: string;
  file?: File | null;
}): Promise<NotesEvaluationResponse> {
  const body = new FormData();
  body.append("examName", request.examName);
  body.append("questionPaperText", request.questionPaperText);
  body.append("answerText", request.answerText);
  body.append("language", request.language);
  if (request.file) {
    body.append("file", request.file);
  }

  const response = await fetch(`${API_BASE_URL}/notes/evaluate`, {
    method: "POST",
    body
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
}

async function readErrorMessage(response: Response) {
  try {
    const data = await response.json();
    return toUserMessage(response.status, data.message ?? data.error);
  } catch {
    return toUserMessage(response.status);
  }
}

function authHeaders(accessToken: string): Record<string, string> {
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}

function authJsonHeaders(accessToken: string): Record<string, string> {
  return {
    "Content-Type": "application/json",
    ...authHeaders(accessToken)
  };
}

function toUserMessage(status: number, message?: string) {
  if (message) {
    return message;
  }

  if (status >= 500) {
    return "JagdiSu AI is temporarily unavailable. Please try again later.";
  }

  return "The quiz could not be generated. Please check the details and try again.";
}
