import { createBrowserRouter, type RouteObject } from "react-router-dom";
import { RequireAuth } from "@/auth/RequireAuth";
import { DashboardLayout } from "@/layouts/DashboardLayout";
import { PublicLayout } from "@/layouts/PublicLayout";
import { CreateQuizPage } from "@/pages/CreateQuizPage";
import { DashboardPage } from "@/pages/DashboardPage";
import { EditQuizMetadataPage } from "@/pages/EditQuizMetadataPage";
import { HomePage } from "@/pages/HomePage";
import { LoginPage } from "@/pages/LoginPage";
import { HostAccessPage } from "@/pages/HostAccessPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { PlayPage } from "@/pages/PlayPage";
import { ProfilePage } from "@/pages/ProfilePage";
import { PlaySessionPage } from "@/pages/PlaySessionPage";
import { QuizQuestionsPage } from "@/pages/QuizQuestionsPage";
import { QuizReviewPage } from "@/pages/QuizReviewPage";
import { QuizzesPage } from "@/pages/QuizzesPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { CreateSessionPage } from "@/pages/CreateSessionPage";
import { SessionDetailsPage } from "@/pages/SessionDetailsPage";
import { SessionLivePage } from "@/pages/SessionLivePage";
import { SessionLobbyPage } from "@/pages/SessionLobbyPage";
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
      { path: "/register", element: <RegisterPage /> },
      { path: "/play", element: <PlayPage /> },
      { path: "/play/:pin", element: <PlaySessionPage /> },
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
          { path: "/profile", element: <ProfilePage /> },
          { path: "/profile/host-access", element: <HostAccessPage /> },
          { path: "/quizzes", element: <QuizzesPage /> },
          { path: "/quizzes/new", element: <CreateQuizPage /> },
          { path: "/quizzes/:quizId", element: <EditQuizMetadataPage /> },
          { path: "/quizzes/:quizId/questions", element: <QuizQuestionsPage /> },
          { path: "/quizzes/:quizId/review", element: <QuizReviewPage /> },
          { path: "/sessions", element: <SessionsPage /> },
          { path: "/sessions/new", element: <CreateSessionPage /> },
          { path: "/sessions/:sessionId", element: <SessionDetailsPage /> },
          { path: "/sessions/:sessionId/lobby", element: <SessionLobbyPage /> },
          { path: "/sessions/:sessionId/play", element: <SessionLivePage /> }
        ]
      }
    ]
  }
];

export const router = createBrowserRouter(routes);
