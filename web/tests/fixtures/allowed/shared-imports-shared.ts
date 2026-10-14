// Fixture: shared may import from shared. The lint must accept this file.
import { format } from "../../../src/shared/lib/format";

export const value = format("ok");
