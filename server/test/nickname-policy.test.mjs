import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { validateNickname } from "../src/nickname-policy.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const vectors = (await readFile(resolve(root, "shared/nickname-vectors.tsv"), "utf8"))
  .split(/\r?\n/)
  .filter((line) => line && !line.startsWith("#"))
  .map((line) => {
    const [input, valid, expected] = line.split("\t");
    return { input, valid: valid === "true", ...(valid === "true" ? { normalized: expected } : { error: expected }) };
  });

test("shared Android/Node nickname validation vectors", () => {
  for (const vector of vectors) {
    const result = validateNickname(vector.input);
    assert.equal(result.ok, vector.valid, JSON.stringify(vector.input));
    if (vector.valid) assert.equal(result.normalized, vector.normalized);
    else assert.equal(result.code, vector.error);
  }
});

test("NFC normalizes decomposed Spanish accents and spaces are rejected", () => {
  assert.equal(validateNickname("ábc").nickname, "ábc");
  assert.equal(validateNickname("  abc").code, "NICKNAME_INVALID_CHARACTERS");
});
