# Install Mint

 - You will need Postgres installed and running locally.
 - Create a Postgres user to own the Mint database, maybe call her mint
 - For simplicity create a database mint owned by that user
 - execute `createSchema.sql` and `schemaUpdates.sql` from `WEB-INF/src` directory
 - Modify `hibernate.properties` with you database details
 - cd into the mint directory
 - call `TOMCAT_HOME=/your/tomcat9/dir ant -Dappname=mint dist`
 - Copy the `work/dist/mint` directory into your Tomcat webapps directory.

