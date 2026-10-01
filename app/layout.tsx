import type { Metadata } from "next";
import "./globals.css";
import FeedbackChatMount from "./FeedbackChatMount";

export const metadata: Metadata = {
  title: "בורסה",
  description: "בורסה — הבורסה לניירות ערך בתל אביב בטלפון: מדדים, מניות, רשימת מעקב ותיק אישי",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="he" dir="rtl">
      <body>{children}
        <FeedbackChatMount />
      </body>
    </html>
  );
}
