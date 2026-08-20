# Security

The kit renders HTML and sanitises markdown, so a bug here can become an injection in someone's page.
If you find one, please do not open a public issue.

Write to **318698958+thymekit@users.noreply.github.com** with a description and, if you can, the
smallest input that shows it. You will get an answer, and the fix will credit you unless you ask
otherwise.

## What is in scope

- Markup or attributes escaping the sanitiser (`MarkdownRenderer`, the `#md` expression object).
- Consumer data reaching the output unescaped through an element's descriptor.
- Anything that lets a page break out of the element it was rendered into.
