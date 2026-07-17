import { setupServer } from "msw/node";
import { handlers } from "@/test/handlers";

/** The MSW server every test runs against (lifecycle in setup.ts). */
export const server = setupServer(...handlers);
