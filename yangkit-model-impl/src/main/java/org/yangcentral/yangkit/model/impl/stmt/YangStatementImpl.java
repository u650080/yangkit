package org.yangcentral.yangkit.model.impl.stmt;

import org.yangcentral.yangkit.base.*;
import org.yangcentral.yangkit.common.api.Namespace;
import org.yangcentral.yangkit.common.api.QName;
import org.yangcentral.yangkit.common.api.exception.ErrorTag;
import org.yangcentral.yangkit.common.api.exception.Severity;
import org.yangcentral.yangkit.common.api.validate.ValidatorRecordBuilder;
import org.yangcentral.yangkit.common.api.validate.ValidatorResult;
import org.yangcentral.yangkit.common.api.validate.ValidatorResultBuilder;
import org.yangcentral.yangkit.model.api.stmt.*;
import org.yangcentral.yangkit.model.api.stmt.Module;
import org.yangcentral.yangkit.register.*;
import org.yangcentral.yangkit.util.ModelUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class YangStatementImpl implements YangStatement {
   private YangContext context;
   private Position position;
   private String argStr;
   private List<YangElement> subElements = new ArrayList();
   private List<YangUnknown> unknowns = new ArrayList();
   //protected boolean isBuilt;
   protected boolean isBuilding;
   //protected boolean isValidated;
   protected boolean isValidating;
   //private boolean init;
   private BuildPhase buildPhase;
   private YangStatement parentStmt;
   private ValidatorResult validatorResult;
   private ValidatorResult initResult;
   private int lastSeq =0;
   private int seq = 0;
   private Map<BuildPhase, ValidatorResult> phaseResultMap = new ConcurrentHashMap();
   private boolean isError = false;
   private YangStatement clonedBy;
   private boolean cleared = true;

   public YangStatementImpl(String argStr) {
      this.argStr = argStr;
   }

   public ValidatorResult afterValidate() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      ValidatorResult selfResult = this.afterValidateSelf();
      validatorResultBuilder.merge(selfResult);
      validatorResultBuilder.merge(this.afterValidateChildren());
      return validatorResultBuilder.build();
   }

   @Override
   public boolean changed() {
      if(seq != lastSeq){
         return true;
      }
      for(YangElement subElement:subElements){
         if(subElement instanceof YangElement){
            continue;
         }
         YangStatement yangStatement = (YangStatement) subElement;
         if(yangStatement.changed()){
            return true;
         }
      }
      return false;
   }

   protected ValidatorResult afterValidateSelf() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      if (this instanceof Referencable) {
         Referencable referencable = (Referencable)this;
         if(!referencable.getReferencedBy().isEmpty()){
            List<YangStatement> unAvailableStatements = new ArrayList<>();
            for(int i = 0; i < referencable.getReferencedBy().size();i++){
               YangStatement ref = referencable.getReferencedBy().get(i);
               if(!ModelUtil.isAvailableStatement(ref)){
                  unAvailableStatements.add(ref);
               }
            }
            for(YangStatement ref:unAvailableStatements){
               referencable.delReference(ref);
            }
         }
         if (!referencable.isReferenced()) {
            ErrorCode errorCode = null;
            if (referencable instanceof Typedef) {
               errorCode = ErrorCode.UNUSED_TYPEDEF;
            } else if (referencable instanceof Grouping) {
               errorCode = ErrorCode.UNUSED_GROUPING;
            } else {
               errorCode = ErrorCode.UNUSED_IMPORT;
            }
            validatorResultBuilder.addRecord(ModelUtil.reportError(this,Severity.WARNING,ErrorTag.BAD_ELEMENT,
                    errorCode.toString(new String[]{"name=" + this.getArgStr()})));
         }
      }

      return validatorResultBuilder.build();
   }

   protected ValidatorResult afterValidateChildren() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      Iterator elementIterator = this.subElements.iterator();

      while(elementIterator.hasNext()) {
         YangElement subElement = (YangElement)elementIterator.next();
         if (subElement instanceof YangStatement) {
            YangStatement statement = (YangStatement)subElement;
            if (!statement.isErrorStatement()) {
               validatorResultBuilder.merge(statement.afterValidate());
            }
         }
      }

      return validatorResultBuilder.build();
   }

   public void setArgStr(String argStr) {
      this.argStr = argStr;
      seq++;
   }

   public Position getElementPosition() {
      if(position == null){
         if(context != null){
            Module curModule = context.getCurModule();
            String moduleName = curModule.getArgStr();
            String revision = curModule.getCurRevisionDate().isPresent()?curModule.getCurRevisionDate().get():null;
            String source = moduleName +  "@" +(revision==null?"":revision);
            Position pos = new Position(source,new YangStatementLocation(this));
            position = pos;
         }
      }
      return this.position;
   }

   public void setElementPosition(Position position) {
      this.position = position;
   }

   public String getArgStr() {
      return this.argStr;
   }

   public List<YangElement> getSubElements() {
      return this.subElements;
   }

   public List<YangStatement> getSubStatement(QName keyword) {
      List<YangStatement> matched = new ArrayList();
      Iterator yangElementIterator = this.subElements.iterator();

      while(yangElementIterator.hasNext()) {
         YangElement element = (YangElement)yangElementIterator.next();
         if (element instanceof YangStatement) {
            YangStatement subStatement = (YangStatement)element;
            if (subStatement.getYangKeyword().equals(keyword)) {
               matched.add(subStatement);
            }
         }
      }

      return matched;
   }

   @Override
   public YangStatement getSubStatement(QName keyword, String arg) {
      List<YangStatement> matched = getSubStatement(keyword);
      if(matched == null || matched.isEmpty()){
         return null;
      }
      if(arg == null){
         return matched.get(0);
      }
      for(YangStatement statement:matched){
         if(statement.getArgStr().equals(arg)){
            return statement;
         }
      }
      return null;
   }

   public List<YangUnknown> getUnknowns() {
      return this.unknowns;
   }

   @Override
   public List<YangUnknown> getUnknowns(QName keyword) {
      List<YangUnknown> matched = new ArrayList<>();
      for(YangUnknown unknown:unknowns){
         if (unknown.getYangKeyword().equals(keyword)) {
            matched.add(unknown);
         }
      }
      return matched;
   }

   @Override
   public YangUnknown getUnknown(QName keyword, String arg) {
      if(arg == null){
         List<YangUnknown> unknownList = getUnknowns(keyword);
         if(unknownList.isEmpty()){
            return null;
         }
         return unknownList.get(0);
      }
      for(YangUnknown unknown:unknowns){
         if(unknown.getYangKeyword() == null || unknown.getArgStr() == null){
            continue;
         }
         if (unknown.getYangKeyword().equals(keyword) && unknown.getArgStr().equals(arg)) {
            return unknown;
         }
      }
      return null;
   }

   private List<YangUnknown> getUnknowns(String keyword) {
      List<YangUnknown> targetUnknowns = new ArrayList();
      Iterator unknownIterator = this.unknowns.iterator();

      while(unknownIterator.hasNext()) {
         YangUnknown unknown = (YangUnknown)unknownIterator.next();
         if (unknown.getKeyword().equals(keyword)) {
            targetUnknowns.add(unknown);
         }
      }

      return targetUnknowns;
   }

   private boolean isBuilt(){
      if(validatorResult == null){
         return false;
      }
      YangStatementParserPolicy policy = YangStatementRegister.getInstance().getStatementParserPolicy(this.getYangKeyword());
      if(policy !=null){
         if (policy.isLastPhase(this.buildPhase)&&validatorResult.isOk()){
            return true;
         }
      }
      if(this instanceof DefaultYangUnknown){
         if(this.buildPhase == BuildPhase.SCHEMA_TREE && validatorResult.isOk()){
            return true;
         }
      }
      return false;
   }
   protected ValidatorResult validateSelf() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      return validatorResultBuilder.build();
   }

   protected ValidatorResult validateChildren() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      Iterator elementIterator = this.subElements.iterator();

      while(elementIterator.hasNext()) {
         YangElement subElement = (YangElement)elementIterator.next();
         if (subElement instanceof YangStatement) {
            YangStatement statement = (YangStatement)subElement;
            if (!statement.isErrorStatement()) {
               validatorResultBuilder.merge(statement.validate());
            }
         }
      }

      return validatorResultBuilder.build();
   }

   public ValidatorResult validate() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
//      if (this.isValidated()) {
//         return validatorResultBuilder.build();
//      } else
         if (this.isValidating) {
         validatorResultBuilder.addRecord(ModelUtil.reportError(this,
                 ErrorCode.CIRCLE_REFERNCE.getFieldName()));
         return validatorResultBuilder.build();
      } else {
         this.isValidating = true;
         if(this.isBuilt()){
            ValidatorResult selfResult = this.validateSelf();
            this.setValidateResult(selfResult);
            validatorResultBuilder.merge(selfResult);
         }

         validatorResultBuilder.merge(this.validateChildren());
         ValidatorResult result = validatorResultBuilder.build();
//         if (result.isOk()) {
//            this.isValidated = true;
//         }

         this.isValidating = false;
         return result;
      }
   }

   public synchronized ValidatorResult build() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      BuildPhase[] buildPhases = BuildPhase.values();
      int length = buildPhases.length;

      for(int i = 0; i < length; ++i) {
         BuildPhase phase = buildPhases[i];
         ValidatorResult phaseResult = this.build(phase);
         validatorResultBuilder.merge(phaseResult);
         if (!phaseResult.isOk()) {
            break;
         }
         if(phase == BuildPhase.GRAMMAR){
            ValidatorResult result = buildUnknowns();
            if(!result.isOk()){
               break;
            }
            validatorResultBuilder.merge(result);
         }
      }

      ValidatorResult result = validatorResultBuilder.build();
//      if (result.isOk()) {
//         this.setBuilt(true);
//      }
      return result;
   }

   public synchronized ValidatorResult build(BuildPhase buildPhase) {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      if (this.isBuilding) {
         validatorResultBuilder.addRecord(ModelUtil.reportError(this,
                 ErrorCode.CIRCLE_REFERNCE.getFieldName()));
         return validatorResultBuilder.build();
      } else {
         this.isBuilding = true;
         YangStatementParserPolicy parserPolicy = YangStatementRegister.getInstance().getStatementParserPolicy(this.getYangKeyword());
         if (parserPolicy == null && !(this instanceof DefaultYangUnknown)) {
            validatorResultBuilder.addRecord(ModelUtil.reportError(this,
                    ErrorCode.MISSING_CLASS_REG.toString(new String[]{"keyword=" + this.getYangKeyword().getQualifiedName()})));
            return validatorResultBuilder.build();
         } else {
            ValidatorResult selfResult;
            if ((parserPolicy!= null && parserPolicy.getPhases().contains(buildPhase))
            || (this instanceof DefaultYangUnknown)) {
               if (this.phaseResultMap.containsKey(buildPhase)) {
                  validatorResultBuilder.merge(this.phaseResultMap.get(buildPhase));
               } else {

                  if(this.getValidateResult() != null && this.getValidateResult().isOk()){
                     this.buildPhase = buildPhase;
                     selfResult = this.buildSelf(buildPhase);
                     this.phaseResultMap.put(buildPhase, selfResult);
                     this.setValidateResult(selfResult);
                     validatorResultBuilder.merge(selfResult);
                  }

               }
            }

            validatorResultBuilder.merge(this.buildChildren(buildPhase));
            this.isBuilding = false;
            return validatorResultBuilder.build();
         }
      }
   }

   protected ValidatorResult buildSelf(BuildPhase phase) {
      switch (phase) {
         case GRAMMAR:
            if (this.getContext() != null && this.getContext().getNamespace() == null) {
               this.getContext().setNamespace(ModelUtil.getNamespace(this.getContext().getCurModule()));
            }
         default:
            return (new ValidatorResultBuilder()).build();
      }
   }

   protected ValidatorResult buildChildren(BuildPhase phase) {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      List<YangUnknown> buildUnknowns = null;
      Iterator elementIterator = this.subElements.iterator();

      while(elementIterator.hasNext()) {
         YangElement subElement = (YangElement)elementIterator.next();
         if (!(subElement instanceof YangComment)) {
            if (subElement instanceof YangUnknownBlock) {
               if (BuildPhase.GRAMMAR == phase) {
                  YangUnknown unknown = ((YangUnknownBlock)subElement).build(this.getContext());
                  if (buildUnknowns == null) {
                     buildUnknowns = new ArrayList();
                  }

                  buildUnknowns.add(unknown);
               }
            } else {
               YangStatement statement = (YangStatement)subElement;
               if (!statement.isErrorStatement()) {
                  validatorResultBuilder.merge(statement.build(phase));
               }
            }
         }
      }

      if (buildUnknowns != null) {
         elementIterator = buildUnknowns.iterator();

         while(elementIterator.hasNext()) {
            YangUnknown yangUnknown = (YangUnknown)elementIterator.next();
            this.addChild(yangUnknown);
            yangUnknown.setContext(new YangContext(this.getContext()));
            yangUnknown.init();
            validatorResultBuilder.merge(yangUnknown.build(phase));
         }
      }

      return validatorResultBuilder.build();
   }

   public ValidatorResult getValidateResult() {
      return this.validatorResult;
   }

   public void setValidateResult(ValidatorResult validatorResult) {
      if (this.validatorResult == null) {
         this.validatorResult = validatorResult;
      } else {
         ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
         validatorResultBuilder.merge(this.validatorResult);
         validatorResultBuilder.merge(validatorResult);
         this.validatorResult = validatorResultBuilder.build();
      }
   }

//   public boolean isBuilt() {
//      if (this.isBuilt) {
//         return true;
//      } else if (!this.isInit()) {
//         return false;
//      } else {
//         YangStatementParserPolicy parserPolicy = YangStatementRegister.getInstance().getStatementParserPolicy(this.getYangKeyword());
//         if (parserPolicy == null) {
//            return false;
//         } else if (this.getValidateResult() != null && this.getValidateResult().isOk()) {
//            if (!parserPolicy.getPhases().isEmpty() && !parserPolicy.isLastPhase(this.buildPhase)) {
//               return false;
//            } else {
//               if (this.subElements.size() > 0) {
//                  Iterator elementIterator = this.subElements.iterator();
//
//                  while(elementIterator.hasNext()) {
//                     YangElement subElement = (YangElement)elementIterator.next();
//                     if (subElement instanceof YangStatement) {
//                        YangStatement subStatement = (YangStatement)subElement;
//                        if (!subStatement.isBuilt()) {
//                           return false;
//                        }
//                     }
//                  }
//               }
//
//               this.isBuilt = true;
//               return this.isBuilt;
//            }
//         } else {
//            return false;
//         }
//      }
//   }

   public boolean isBuilding() {
      return this.isBuilding;
   }

//   public boolean isValidated() {
//      return this.isValidated;
//   }

//   public void setBuilt(boolean built) {
//      this.isBuilt = built;
//   }

   public boolean checkChild(YangStatement subStatement){
      if(context == null){
         return true;
      }
      YangSpecification yangSpecification = context.getYangSpecification();
      if(yangSpecification == null){
         return true;
      }
      YangStatementDef statementDef = yangSpecification.getStatementDef(this.getYangKeyword());
      if(statementDef == null){
         return true;
      }
      if(subStatement instanceof DefaultYangUnknown){
         return true;
      }
      Cardinality cardinality = statementDef.getSubStatementCardinality(subStatement.getYangKeyword());
      if(cardinality == null){
         return false;
      }
      if(cardinality.isUnbounded()){
         return true;
      }
      List<YangStatement> matched = this.getSubStatement(subStatement.getYangKeyword());
      if((matched.size() +1) > cardinality.getMaxElements()){
         return false;
      }
      return true;
   }
   public boolean addChild(YangElement yangElement) {
      boolean result = this.subElements.add(yangElement);
      if (!result) {
         return false;
      } else {
         if (yangElement instanceof YangStatement) {
            YangStatementImpl yangStatement = (YangStatementImpl)yangElement;
            yangStatement.setParentStatement(this);
         }
         seq++;
         return true;
      }
   }

   public boolean addChild(int index, YangElement yangElement) {
      this.subElements.add(index, yangElement);
      if (yangElement instanceof YangStatement) {
         YangStatementImpl yangStatement = (YangStatementImpl)yangElement;
         yangStatement.setParentStatement(this);
      }
      seq++;
      return true;
   }

   @Override
   public int getChildIndex(YangElement child) {
      for(int i =0; i< subElements.size();i++){
         if(subElements.get(i) == child){
            return i;
         }
      }
      return -1;
   }

   public boolean updateChild(int index, YangElement yangElement) {
      if (this.subElements.get(index) == null) {
         return false;
      } else {
         this.subElements.set(index, yangElement);
         if (yangElement instanceof YangStatement) {
            YangStatementImpl yangStatement = (YangStatementImpl)yangElement;
            yangStatement.setParentStatement(this);
         }
         seq++;
         return true;
      }
   }

   public boolean updateChild(YangStatement statement) {
      int index = -1;

      for(int i = 0; i < this.subElements.size(); ++i) {
         YangElement element = this.subElements.get(i);
         if (element instanceof YangStatement) {
            YangStatement yangStatement = (YangStatement)element;
            if (yangStatement.equals(statement)) {
               index = i;
            }
         }
      }

      if (-1 == index) {
         return false;
      } else {
         return this.updateChild(index, statement);
      }
   }

   public boolean removeChild(YangElement yangElement) {
      int pos = -1;

      for(int i = 0; i < this.subElements.size(); ++i) {
         YangElement subElement = this.subElements.get(i);
         if (subElement == yangElement) {
            pos = i;
            break;
         }
      }

      if (-1 == pos) {
         return false;
      } else {
         YangElement element = this.subElements.remove(pos);
         if (element instanceof YangStatement) {
            YangStatementImpl statement = (YangStatementImpl)element;
            if(statement.getParentStatement() == this){
               statement.setParentStatement(null);
            }

         }
         seq++;
         return true;
      }
   }

   public void setChildren(List<YangElement> yangElements) {
      this.subElements.clear();
      if (null != yangElements) {
         Iterator elementIterator = yangElements.iterator();

         while(elementIterator.hasNext()) {
            YangElement yangElement = (YangElement)elementIterator.next();
            if (null != yangElement) {
               this.addChild(yangElement);
            }
         }

      }
   }

   public YangStatement getParentStatement() {
      return this.parentStmt;
   }

   public void setParentStatement(YangStatement parentStatement) {
      this.parentStmt = parentStatement;
   }

   public void clear(){
      clearSelf();
      for(YangElement element:subElements){
         if(element instanceof YangStatement){
            YangStatement subStatement = (YangStatement) element;
            subStatement.clear();
         }
      }
   }
   protected void clearSelf() {
      if(cleared){
         return;
      }
      this.unknowns.clear();
      this.isBuilding = false;
      this.isValidating = false;
      this.buildPhase = null;
      this.validatorResult = null;
      this.isError = false;
      this.clonedBy = null;
      this.initResult = null;
      this.phaseResultMap.clear();
      lastSeq = 0;
      seq = 0;
      cleared = true;
   }

   public YangContext getContext() {
      return this.context;
   }

   public void setContext(YangContext context) {
      this.context = context;
      context.setSelf(this);
   }

   protected ValidatorResult initChildren() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      for(YangElement subElement:subElements){
         if(subElement == null){
            continue;
         }
         if(!(subElement instanceof YangStatement)){
            continue;
         }
         if(((YangStatement) subElement).isErrorStatement()){
            continue;
         }
         YangStatement yangStatement = (YangStatement) subElement;
         YangContext childContext = yangStatement.getContext();
         if((childContext == null)){
            childContext = new YangContext(context);
         } else {
            childContext.merge(context);
         }
         if(this instanceof Grouping){
            childContext.setCurGrouping((Grouping) this);
         }
         yangStatement.setContext(childContext);
         validatorResultBuilder.merge(yangStatement.init());
      }
      return validatorResultBuilder.build();
   }

   protected ValidatorResult initSelf() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      if (this instanceof Identifiable) {
         if (!ModelUtil.isIdentifier(this.getArgStr())) {
            validatorResultBuilder.addRecord(ModelUtil.reportError(this,
                    ErrorCode.INVALID_IDENTIFIER.getFieldName() + " argument:" + this.getArgStr()));
         }
      } else if (this instanceof IdentifierRef && !ModelUtil.isIdentifierRef(this.getArgStr())) {
         validatorResultBuilder.addRecord(ModelUtil.reportError(this,
                 ErrorCode.INVALID_IDENTIFIER_REF.getFieldName() + " argument:" + this.getArgStr()));
      }

      Iterator<YangElement> elementIterator = this.subElements.iterator();
      while (elementIterator.hasNext()) {
         YangElement subElement = elementIterator.next();
         if (!(subElement instanceof YangStatement)) {
            continue;
         }
         if (subElement instanceof YangUnknown) {
            unknowns.add((YangUnknown) subElement);
         }
      }
      return validatorResultBuilder.build();
   }

   public ValidatorResult init() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      if(lastSeq != seq){
         lastSeq = seq;
      }
      cleared = false;
      ValidatorResult result;
      if(initResult != null){
         result =  initResult;
      } else {
         result = this.initSelf();
         initResult = result;
         this.setValidateResult(result);
      }

      validatorResultBuilder.merge(result);
      validatorResultBuilder.merge(this.initChildren());
      result = validatorResultBuilder.build();
//      if (result.isOk()) {
//         this.init = true;
//      }

      return result;
   }

//   public boolean isInit() {
//      return this.init;
//   }

   public <T extends YangStatement> T getSelf() {
      return (T) this;
   }

   public YangStatement clonedBy() {
      return this.clonedBy;
   }

   public YangStatement clone() {
      try {
         Constructor<? extends YangStatement> constructor = null;
         YangStatement clonedStatement = null;
         if (this instanceof YangUnknown) {
            constructor = this.getClass().getConstructor(String.class, String.class);
            clonedStatement = (YangStatement)constructor.newInstance(((YangUnknown)this).getKeyword(), this.getArgStr());
         } else {
            constructor = this.getClass().getConstructor(String.class);
            clonedStatement = (YangStatement)constructor.newInstance(this.getArgStr());
         }
         //clonedStatement.setElementPosition(this.getElementPosition());
         Iterator elementIterator = this.getSubElements().iterator();

         while(elementIterator.hasNext()) {
            YangElement subElement = (YangElement)elementIterator.next();
            if (subElement instanceof YangStatement) {
               YangStatement subStatement = (YangStatement)subElement;
               clonedStatement.addChild(subStatement.clone());
            }
         }

         ((YangStatementImpl)clonedStatement).clonedBy = this;
         return clonedStatement;
      } catch (NoSuchMethodException e) {
         e.printStackTrace();
      } catch (InvocationTargetException e) {
         e.printStackTrace();
      } catch (InstantiationException e) {
         e.printStackTrace();
      } catch (IllegalAccessException e) {
         e.printStackTrace();
      }

      return null;
   }

   public String toString() {
      StringBuffer sb = new StringBuffer();
      QName keyword = this.getYangKeyword();
      if (keyword.getNamespace().equals(Yang.NAMESPACE.getUri())) {
         sb.append(keyword.getLocalName());
      } else {
         sb.append(keyword.getQualifiedName());
      }

      if (this.getArgStr() != null) {
         sb.append(" ");
         sb.append(this.getArgStr());
      }

      return sb.toString();
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof YangStatementImpl)) {
         return false;
      } else {
         YangStatementImpl that = (YangStatementImpl)o;
         return Objects.equals(this.getArgStr(), that.getArgStr()) && Objects.equals(this.getYangKeyword(), that.getYangKeyword());
      }
   }

   public int hashCode() {
      return Objects.hash(new Object[]{this.getArgStr()});
   }

   public boolean isErrorStatement() {
      return this.isError;
   }

   public void setErrorStatement(boolean errorStatement) {
      this.isError = errorStatement;
   }

   public List<YangStatement> getEffectiveSubStatements() {
      List<YangStatement> subStatements = new ArrayList();
      Iterator unknownIterator = this.unknowns.iterator();

      while(unknownIterator.hasNext()) {
         YangUnknown unknown = (YangUnknown)unknownIterator.next();
         subStatements.add(unknown);
      }

      return subStatements;
   }

   private YangUnknown transformUnknown(YangUnknown yangUnknown){
      if(!(yangUnknown instanceof DefaultYangUnknown)){
         return yangUnknown;
      }
      Extension extension = yangUnknown.getExtension();
      if(extension == null){
         return yangUnknown;
      }
      Namespace namespace = extension.getContext().getNamespace();
      YangUnknown newUnknown = (YangUnknown) YangStatementRegister.getInstance().getYangStatementInstance(
              new QName(namespace, extension.getArgStr()), yangUnknown.getArgStr());
      if(newUnknown != null){
         return newUnknown;
      }
      return yangUnknown;
   }
   private ValidatorResult buildUnknowns(){
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      int size = unknowns.size();
      for(int i = 0; i < size;i++){
         YangUnknown unknown = unknowns.get(i);
         YangUnknown newUnknown = transformUnknown(unknown);
         if(unknown != newUnknown){
            int index = getChildIndex(unknown);
            this.updateChild(index,newUnknown);
         }
      }
      if(this.changed()){
         this.clear();
         validatorResultBuilder.merge(init());
         validatorResultBuilder.merge(build(BuildPhase.LINKAGE));
         validatorResultBuilder.merge(build(BuildPhase.GRAMMAR));
      }

      return validatorResultBuilder.build();
   }
}
