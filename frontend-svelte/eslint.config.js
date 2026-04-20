import js from "@eslint/js";
import ts from "typescript-eslint";
import svelte from "eslint-plugin-svelte";
import globals from "globals";

/** @type {import('eslint').Linter.Config[]} */
export default [
	js.configs.recommended,
	...ts.configs.recommended,
	...svelte.configs["flat/recommended"],
	{
		languageOptions: {
			globals: {
				...globals.browser,
				...globals.node
			}
		}
	},
	{
		files: ["**/*.svelte"],
		languageOptions: {
			parserOptions: {
				parser: ts.parser
			}
		}
	},
	{
		files: ["**/*.ts"],
		languageOptions: {
			parser: ts.parser,
			parserOptions: {
				project: "./tsconfig.json",
				extraFileExtensions: [".svelte"]
			}
		}
	},
	{
		rules: {
			"@typescript-eslint/no-explicit-any": "off",
			"svelte/no-navigation-without-resolve": "off",
			"svelte/prefer-svelte-reactivity": "off",
			"svelte/no-useless-children-snippet": "off",
			"@typescript-eslint/no-unused-vars": ["error", { "argsIgnorePattern": "^_" }]
		}
	},
	{
		ignores: ["build/", ".svelte-kit/", "dist/", "node_modules/"]
	}
];
