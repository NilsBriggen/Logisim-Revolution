# Glossary: what the interface used to be called

The English wording was rewritten to use words people already know rather than words invented by
the program. Nothing about a project file changed: library and component identifiers, the contents
of a `.circ`, and every preference key are exactly as they were. Only what is shown on screen is
different.

This table is here so course notes, tutorials and screenshots written against earlier releases can
be brought up to date without having to hunt for each change.

## Tools

| Was | Is now | Why |
| --- | --- | --- |
| Poke Tool | Interact | "Poke" is the program's own word. What the tool does is interact with a running circuit. |
| Select Tool | Select | The suffix said nothing: everything on that toolbar is a tool. |
| Edit Tool | Edit | As above. |
| Wiring Tool | Wire | As above, and the verb is what you are about to do. |
| Text Tool | Text | As above. |
| Menu Tool | Component menu | It opens a component's own menu; "menu tool" describes the mechanism, not the purpose. |
| Change values within circuit | Change values in the running circuit | Says when the tool applies. |
| Edit circuit components | Select and move components | Says what it actually does. |
| Add wires to circuit | Draw wires between components | As above. |
| Edit text in circuit | Add and edit labels | "Text in circuit" is a label. |

## Panels and groups

| Was | Is now | Why |
| --- | --- | --- |
| Toolbox | Components | It holds components. The word "toolbox" also collided with the toolbar. |
| Plexers | Multiplexers | "Plexer" is not a word outside this program. |
| Explorer | Circuits | The panel lists the project's circuits. |
| Attributes | Properties | The panel is called Properties, and "property" is what every other program calls this. |

The palette's groups — Logic, Wiring & routing, Arithmetic, Memory, Input & output, Displays,
Chips & TTL, Advanced — are new, and are a presentation layer only. The libraries themselves are
unchanged, and a library the palette does not recognise appears whole under its own name.

## Menus

| Was | Is now | Why |
| --- | --- | --- |
| Add Circuit… | New Circuit… | Nothing is being added to; a circuit is being made. |
| Add VHDL Entity… | New VHDL Entity… | As above. |
| Remove Circuit | Delete Circuit | "Remove" suggested it could be put back. |
| Raise To Top / Lower To Bottom | Bring to Front / Send to Back | The usual names for this everywhere else. |
| Raise Selection / Lower Selection | Bring Forward / Send Backward | As above. |
| Can't Undo / Can't Redo | Nothing to Undo / Nothing to Redo | It is not that the program refuses; there is nothing there. |
| Change Attribute | Change Property | Follows the panel. |

## Circuit analysis

The analysis window was missed by the first pass and has been brought in line with the rest.

| Was | Is now | Why |
| --- | --- | --- |
| Combinational Analysis | Circuit Analysis | It matches the menu item that opens it, and says what it analyses. |
| Inputs & Outputs (tab) | Signals | The tab lists both, and "signals" is what they are. |
| Minimized (tab) | Simplified | Plainer, and it is what the tab shows. |
| Set As Expression | Use This Expression | Says what pressing it does. |
| Optimize minterms / maxterms | Simplify (sum of products) / (product of sums) | Names the form you get, which is the actual choice. |
| Export TeX | Export LaTeX… | The name of the format, and the ellipsis that says a dialog follows. |
| Style: / Format: | Map style: / Expression form: | Two controls both labelled with a bare noun sat next to each other. |

## Other wording

| Was | Is now | Why |
| --- | --- | --- |
| Radix | Number base | "Radix" is correct and almost nobody says it. |
| First radix when wire poked | First number base when a wire is clicked | Both of the above at once. |
| Attribute unchanged because request is invalid | The property was not changed because the value is not valid | It reads as a sentence. |
| Move viewed circuit up list | Move this circuit up | The button is in the circuit's own list. |
| Toolbar location: (5 choices) | Toolbar: Shown / Hidden | Three of the five never did anything. |
| Infos (0) | Notes (0) | It was one of four English tab names in an otherwise translated window. |
| Get Circuit Statistics | Circuit Statistics… | A noun phrase, with the ellipsis that says a dialog follows. |
| Edit viewed circuit's appearance | Edit this circuit's appearance | As above. |

## Translations

Only the English bundle changed, and no key was renamed. The other twelve languages keep their own
wording until someone translates the new text, which `LocaleManager` already handles by falling
back to English for a key a language does not have. `StringKeyCoverageTest` checks that every piece
of text the program asks for exists, which is the failure this kind of pass risks.
