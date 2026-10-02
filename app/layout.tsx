import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";
import FeedbackChatMount from "./FeedbackChatMount";

// IBM Plex Sans Hebrew (OFL): Hebrew, Latin and even-width digits in one family —
// the same typeface the phone app bundles.
const plex = localFont({
  src: [
    { path: "./fonts/plex_regular.ttf", weight: "400" },
    { path: "./fonts/plex_medium.ttf", weight: "500" },
    { path: "./fonts/plex_semibold.ttf", weight: "600" },
    { path: "./fonts/plex_bold.ttf", weight: "700" },
  ],
  variable: "--font-plex",
  display: "swap",
});

export const metadata: Metadata = {
  title: "בורסה",
  description: "בורסה — הבורסה לניירות ערך בתל אביב: מדדים, מניות, רשימת מעקב ותיק אישי",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="he" dir="rtl" className={plex.variable}>
      <body>{children}
        <FeedbackChatMount />
      </body>
    </html>
  );
}
