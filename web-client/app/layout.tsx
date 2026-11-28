import "./globals.css";
export const metadata = {
  title: "SmartRuble · Контрагенты",
  description: "Контроль расходов и проверка контрагентов",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}
