# OAMS UML Diagrams

This folder contains seven PlantUML source diagrams for the Phase 1 design:

1. `use_case_diagram.puml` - actors and system use cases
2. `class_diagram.puml` - planned Java classes, packages, relationships, and visibility
3. `sequence_diagram.puml` - buyer bid submission, validation, and outcomes
4. `activity_diagram.puml` - bidding workflow and auction closing
5. `state_machine_diagram.puml` - auction status lifecycle
6. `component_diagram.puml` - GUI, service, DAO, and SQLite layers
7. `deployment_diagram.puml` - desktop runtime and local database deployment

## Rendering

- In VS Code, install a PlantUML extension, open a `.puml` file, and use the extension's preview command.
- With a local PlantUML JAR and a compatible Java installation, render all diagrams from the project root with:

```powershell
java -jar plantuml.jar -tpng docs/uml
```

PlantUML may also render to SVG by changing `-tpng` to `-tsvg`. Rendering with the JAR may require Graphviz depending on the PlantUML version and diagram features.
