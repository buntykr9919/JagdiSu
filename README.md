# AI Exam Quiz Platform

Next.js frontend + Spring Boot backend starter for an AI-based exam quiz website.

## Features

- Login/signup-ready UI with demo login
- Free plan with 10 quiz generations per day
- Mock Pro subscription flow at Rs 20/month
- Quiz setup with subject, exam name, optional chapter, language, question count, and hints
- Backend AI quiz generation endpoint
- Server-side OpenRouter/OpenAI-compatible integration via `OPENROUTER_API_KEY`
- Exam pattern profiles for common Indian exam types
- Pattern-aware question formats: statement-based, one-liner, or mixed
- Balanced options so answer guessing by option length is harder

## Project Structure

```text
.
├── frontend/      # Next.js app
└── backend/       # Spring Boot API
```

## Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:1999`.

## Run Backend

Install Maven, then:

```bash
cd backend
mvn spring-boot:run
```

Backend runs on `http://localhost:2000` and exposes a root status endpoint at `http://localhost:2000/`.

## Subscription

The frontend tracks free daily usage in browser storage. Free users get 10 successful quiz generations per day. After that, the app shows the subscription screen. The current Pro button activates a mock Rs 20/month plan through `POST /api/subscription/subscribe`; connect Razorpay or Stripe there for real payments.

## AI Configuration

The backend does not show any third-party login to students. For quiz generation with OpenRouter, set:

```bash
OPENROUTER_API_KEY=your_openrouter_api_key_here
AI_BASE_URL=https://openrouter.ai/api/v1
AI_MODEL=openai/gpt-4o-mini
```

For internet-based exam pattern discovery, add a search provider later in `ExamPatternService`. The current implementation uses built-in profiles plus retrieval-style seed patterns.

The generator is designed to create original questions inspired by recent previous-year patterns and official/reputed sources such as NCERT, exam bulletins, Drishti-style explainers, and Testbook-style exam analysis. It should not copy previous-year questions or source text verbatim.
