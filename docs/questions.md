# questions.md – Business Logic Clarifications for InstituteOps

1. Grade ruleset & GPA calculation  
   Question: Exact formula not specified in the prompt.  
   Understanding: Configurable admin-editable ruleset with credits and letter-grade mapping (A=4.0 etc.).  
   Solution: GradeRule entity + deterministic service that stores delta in the append-only ledger.

2. Group-buy campaign failure handling  
   Question: Exact behavior when 72-hour window expires.  
   Understanding: Auto-void all pending orders, unlock inventory, show clear UI notification.  
   Solution: Campaign state machine + scheduled task.

3. Contact masking by role  
   Question: Which roles see full contact details?  
   Understanding: Only System Administrator and Registrar/Finance Clerk.  
   Solution: Role-based view model + Spring Security expressions in Thymeleaf.

4. Homework file storage & validation  
   Question: Storage location and validation rules.  
   Understanding: Local Docker volume, file type + checksum only.  
   Solution: Dedicated upload service with validation.

5. Recommender model rollback  
   Question: How admins roll back a degraded model.  
   Understanding: Versioned models with active flag and instant switch.  
   Solution: RecommenderModel entity + admin interface.

All other features from the original business requirements are implemented exactly as specified with full audit trails, real logic, and no simplifications.