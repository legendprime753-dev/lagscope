LagScope (Paper/Purpur 1.21.x)

Build:
- Maven: mvn -DskipTests package

Install:
- Put target/lagscope-1.0.0.jar into plugins/
- Start server once to generate config.yml

Commands:
- /lag
- /lag info
- /lag clear [chunk|radius <blocks>]
- /lag mode <OFF|LOW|MEDIUM|HIGH|EXTREME>
- /lag report
- /lag reload

Permissions:
- lagscope.use (default true)
- lagscope.admin (default op)
- lagscope.tp (default op)
