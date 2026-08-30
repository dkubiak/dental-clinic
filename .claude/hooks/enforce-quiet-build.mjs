// PreToolUse hook (Bash matcher): blocks raw gradlew/npm build-test-lint invocations,
// pointing at scripts/quiet.sh (and the frontend's *:quiet npm scripts) instead — those
// wrappers keep verbose Testcontainers/checkstyle/Playwright output out of agent context.
// See CLAUDE.md, section "Build and test".
let d = "";
process.stdin.on("data", c => d += c);
process.stdin.on("end", () => {
  let cmd;
  try { cmd = JSON.parse(d).tool_input.command || ""; } catch (e) { process.exit(0); }
  if (/quiet\.sh|:quiet/.test(cmd)) process.exit(0);
  if (/gradlew/.test(cmd) && /\b(build|test|check)\b/.test(cmd)) {
    console.error("Use ../scripts/quiet.sh ./gradlew <task> instead of raw gradlew — see CLAUDE.md, section \"Build and test\".");
    process.exit(2);
  }
  if (/\bnpm\b/.test(cmd) && /\b(test|e2e|lint)\b/.test(cmd)) {
    console.error("Use npm run test:quiet / lint:quiet / e2e:quiet instead of raw npm test/run lint/run e2e — see CLAUDE.md, section \"Build and test\".");
    process.exit(2);
  }
  process.exit(0);
});
