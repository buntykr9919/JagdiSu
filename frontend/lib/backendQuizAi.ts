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

export type SubscriptionResponse = {
  plan: string;
  dailyQuizLimit: number;
  monthlyPriceInr: number;
  paymentRequired: boolean;
  note: string;
};

export async function subscribeToPlan(plan: "PRO" | "ADVANCED", paymentReference: string): Promise<SubscriptionResponse> {
  const response = await fetch(`${API_BASE_URL}/subscription/subscribe`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ plan, paymentMethod: "UPI", paymentReference })
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json();
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
  score: number;
  maxScore: number;
  feedback: string;
};

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

function toUserMessage(status: number, message?: string) {
  if (message) {
    return message;
  }

  if (status >= 500) {
    return "JagdiSu AI is temporarily unavailable. Please try again later.";
  }

  return "The quiz could not be generated. Please check the details and try again.";
}
