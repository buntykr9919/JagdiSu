import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "JagdiSu",
  description: "Generate exam-pattern quizzes."
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
