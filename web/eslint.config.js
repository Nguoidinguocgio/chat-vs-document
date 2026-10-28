import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import boundaries from 'eslint-plugin-boundaries'

// Luật kiến trúc SDD 3.3 (T01 mục 3.3). Mỗi luật có ca vi phạm trong src/__lint_fixtures__ và test ở lint.test.ts.

// no-restricted-syntax / no-restricted-imports bị ghi đè cả danh sách khi cấu hình theo thư mục,
// nên dựng danh sách từ các mục có khóa rồi bỏ bớt mục được miễn ở từng thư mục.
const SYNTAX = {
  html: [
    { selector: "AssignmentExpression[left.property.name=/^(innerHTML|outerHTML)$/]", message: 'R-4: không dùng innerHTML/outerHTML; render qua shared/lib/markdown.' },
    { selector: "CallExpression[callee.property.name='insertAdjacentHTML']", message: 'R-4: không dùng insertAdjacentHTML; render qua shared/lib/markdown.' },
  ],
  pdfjs: [
    { selector: 'ImportExpression[source.value=/^pdfjs-dist/]', message: 'R-5: pdfjs-dist chỉ được import động trong features/source-viewer.' },
  ],
  token: [
    {
      selector: "CallExpression[callee.property.name='setItem'][callee.object.name=/^(localStorage|window\\.localStorage)$/][arguments.0.value=/token|sid/i]",
      message: 'Không lưu token/sid trong localStorage.',
    },
    {
      selector: "CallExpression[callee.property.name='setItem'][callee.object.property.name='localStorage'][arguments.0.value=/token|sid/i]",
      message: 'Không lưu token/sid trong localStorage.',
    },
  ],
  hex: [
    { selector: 'Literal[value=/#[0-9a-fA-F]{6}\\b|#[0-9a-fA-F]{3}\\b/]', message: 'Không dùng mã màu cứng; dùng token trong shared/ui/tokens.css.' },
    { selector: 'TemplateElement[value.raw=/#[0-9a-fA-F]{6}\\b|#[0-9a-fA-F]{3}\\b/]', message: 'Không dùng mã màu cứng; dùng token trong shared/ui/tokens.css.' },
  ],
}
const syntax = (...except) => ['error', ...Object.entries(SYNTAX).filter(([k]) => !except.includes(k)).flatMap(([, v]) => v)]

const IMPORTS = {
  pdfjs: { group: ['pdfjs-dist', 'pdfjs-dist/*'], allowTypeImports: true, message: 'R-5: pdfjs-dist chỉ được import động (import()) trong features/source-viewer.' },
  analytics: { group: ['@/shared/lib/analytics/*', '**/analytics/*'], message: 'R-6: chỉ shared/lib/analytics gửi analytics; dùng API công khai @/shared/lib/analytics.' },
  reka: { group: ['reka-ui', 'reka-ui/*'], message: 'Không import Reka UI ngoài shared/ui (T02 mục 3.4).' },
}
const imports = (...except) => ['error', { patterns: Object.entries(IMPORTS).filter(([k]) => !except.includes(k)).map(([, v]) => v) }]

const NETWORK_GLOBALS = [
  { name: 'fetch', message: 'R-3: chỉ shared/api được gọi fetch.' },
  { name: 'EventSource', message: 'R-3: chỉ shared/realtime được dùng EventSource.' },
  { name: 'XMLHttpRequest', message: 'R-3: chỉ shared/api và features/documents/upload được dùng XMLHttpRequest.' },
]
const BEACON = [
  { object: 'navigator', property: 'sendBeacon', message: 'R-6: chỉ shared/lib/analytics gửi analytics.' },
  { object: 'window', property: 'fetch', message: 'R-3: chỉ shared/api được gọi fetch.' },
]

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage', 'public', 'src/__lint_fixtures__/**'] },
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: { parserOptions: { parser: tseslint.parser } },
  },
  {
    files: ['src/**/*.{ts,vue}'],
    plugins: { boundaries },
    settings: {
      'import/resolver': { typescript: { project: './tsconfig.json' } },
      'boundaries/include': ['src/**/*'],
      'boundaries/elements': [
        { type: 'app', pattern: 'src/app', mode: 'folder' },
        { type: 'feature', pattern: 'src/features/*', mode: 'folder', capture: ['name'] },
        { type: 'shared', pattern: 'src/shared/*', mode: 'folder', capture: ['name'] },
        { type: 'locales', pattern: 'src/locales', mode: 'folder' },
        { type: 'mocks', pattern: 'src/mocks', mode: 'folder' },
      ],
    },
    rules: {
      // R-1: shared không import features/app · R-2: features chỉ import feature khác qua index.ts
      'boundaries/element-types': [
        'error',
        {
          default: 'disallow',
          message: 'R-1/R-2: ${file.type} không được import ${dependency.type}.',
          rules: [
            { from: 'app', allow: ['app', 'feature', 'shared', 'locales', 'mocks'] },
            { from: 'feature', allow: ['feature', 'shared', 'locales'] },
            { from: 'shared', allow: ['shared', 'locales'] },
            { from: 'locales', allow: [] },
            { from: 'mocks', allow: ['shared', 'mocks', 'locales'] },
          ],
        },
      ],
      'boundaries/entry-point': [
        'error',
        {
          default: 'disallow',
          message: 'R-2: chỉ import ${dependency.type} qua index.ts của nó.',
          rules: [
            { target: 'feature', allow: 'index.ts' },
            { target: ['app', 'shared', 'locales', 'mocks'], allow: '**' },
          ],
        },
      ],
      'no-restricted-globals': ['error', ...NETWORK_GLOBALS],
      'no-restricted-properties': ['error', ...BEACON],
      'vue/no-v-html': 'error',
      'no-restricted-syntax': syntax(),
      'no-restricted-imports': imports(),

      'vue/multi-word-component-names': 'off',
      // Định dạng mã không thuộc phạm vi lint kiến trúc
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/html-indent': 'off',
      'vue/first-attribute-linebreak': 'off',
    },
  },
  // Ngoại lệ có chủ đích (mỗi khối chỉ mở đúng luật của thư mục đó)
  { files: ['src/shared/api/**'], rules: { 'no-restricted-globals': ['error', NETWORK_GLOBALS[1]], 'no-restricted-properties': ['error', BEACON[0]] } },
  { files: ['src/shared/realtime/**'], rules: { 'no-restricted-globals': ['error', NETWORK_GLOBALS[0], NETWORK_GLOBALS[2]] } },
  { files: ['src/features/documents/upload/**'], rules: { 'no-restricted-globals': ['error', NETWORK_GLOBALS[0], NETWORK_GLOBALS[1]] } },
  { files: ['src/shared/lib/markdown/**'], rules: { 'vue/no-v-html': 'off', 'no-restricted-syntax': syntax('html') } },
  { files: ['src/features/source-viewer/**'], rules: { 'no-restricted-syntax': syntax('pdfjs') } },
  { files: ['src/shared/lib/analytics/**'], rules: { 'no-restricted-imports': imports('analytics'), 'no-restricted-properties': ['error', BEACON[1]] } },
  { files: ['src/shared/ui/**'], rules: { 'no-restricted-imports': imports('reka') } },
  // Test được gọi mạng (MSW), chứa mã màu để kiểm tra tokens, dùng API giả (src/mocks) và helper src/test;
  // mã test không vào bản build nên không áp luật ranh giới R-1/R-2.
  {
    files: ['src/**/*.test.ts', 'src/test/**'],
    rules: {
      'no-restricted-globals': 'off',
      'no-restricted-syntax': syntax('hex'),
      'boundaries/element-types': 'off',
      'vue/one-component-per-file': 'off',
    },
  },
)
