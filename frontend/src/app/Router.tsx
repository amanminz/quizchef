import { createBrowserRouter, type RouteObject } from "react-router-dom";
import { RequireAuth } from "@/auth/RequireAuth";
import { DashboardLayout } from "@/layouts/DashboardLayout";
import { PublicLayout } from "@/layouts/PublicLayout";
import { CreateQuizPage } from "@/pages/CreateQuizPage";
import { DashboardPage } from "@/pages/DashboardPage";
import { EditQuizMetadataPage } from "@/pages/EditQuizMetadataPage";
import { HomePage } from "@/pages/HomePage";
import { LoginPage } from "@/pages/LoginPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { PlayPage } from "@/pages/PlayPage";
import { QuizQuestionsPage } from "@/pages/QuizQuestionsPage";
import { QuizReviewPage } from "@/pages/QuizReviewPage";
import { QuizzesPage } from "@/pages/QuizzesPage";
import { SessionsPage } from "@/pages/SessionsPage";

/**
 * The route table. Public routes render in PublicLayout; everything under
 * RequireAuth renders in DashboardLayout and needs a signed-in user.
 * Exported so tests can mount the same tree in a memory router.
 */
export const routes: RouteObject[] = [
  {
    element: <PublicLayout />,
    children: [
      { path: "/", element: <HomePage /> },
      { path: "/login", element: <LoginPage /> },
      { path: "/play", element: <PlayPage /> },
      { path: "/not-found", element: <NotFoundPage /> },
      { path: "*", element: <NotFoundPage /> }
    ]
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <DashboardLayout />,
        children: [
          { path: "/dashboard", element: <DashboardPage /> },
          { path: "/quizzes", element: <QuizzesPage /> },
          { path: "/quizzes/new", element: <CreateQuizPage /> },
          { path: "/quizzes/:quizId", element: <EditQuizMetadataPage /> },
          { path: "/quizzes/:quizId/questions", element: <QuizQuestionsPage /> },
          { path: "/quizzes/:quizId/review", element: <QuizReviewPage /> },
          { path: "/sessions", element: <SessionsPage /> }
        ]
      }
    ]
  }
];

export const router = createBrowserRouter(routes);
