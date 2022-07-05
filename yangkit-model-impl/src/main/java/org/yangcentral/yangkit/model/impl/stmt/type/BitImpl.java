package org.yangcentral.yangkit.model.impl.stmt.type;

import org.yangcentral.yangkit.base.YangBuiltinKeyword;
import org.yangcentral.yangkit.base.YangContext;
import org.yangcentral.yangkit.common.api.QName;
import org.yangcentral.yangkit.common.api.validate.ValidatorResult;
import org.yangcentral.yangkit.common.api.validate.ValidatorResultBuilder;
import org.yangcentral.yangkit.model.api.restriction.Bits;
import org.yangcentral.yangkit.model.api.stmt.IfFeature;
import org.yangcentral.yangkit.model.api.stmt.Type;
import org.yangcentral.yangkit.model.api.stmt.YangStatement;
import org.yangcentral.yangkit.model.api.stmt.type.Bit;
import org.yangcentral.yangkit.model.api.stmt.type.Position;
import org.yangcentral.yangkit.model.impl.stmt.EntityImpl;
import org.yangcentral.yangkit.model.impl.stmt.IfFeatureSupportImpl;
import org.yangcentral.yangkit.register.YangStatementParserRegister;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class BitImpl extends EntityImpl implements Bit {
   private Position position;
   private IfFeatureSupportImpl ifFeatureSupport = new IfFeatureSupportImpl();

   public BitImpl(String argStr) {
      super(argStr);
   }

   public QName getYangKeyword() {
      return YangBuiltinKeyword.BIT.getQName();
   }

   public Position getPosition() {
      return this.position;
   }

   protected ValidatorResult initSelf() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder(super.initSelf());
      List<YangStatement> matched = this.getSubStatement(YangBuiltinKeyword.POSITION.getQName());
      if (matched.size() != 0) {
         this.position = (Position)matched.get(0);
      }

      return validatorResultBuilder.build();
   }

   public void setContext(YangContext context) {
      super.setContext(context);
      this.ifFeatureSupport.setYangContext(context);
   }

   public List<IfFeature> getIfFeatures() {
      return this.ifFeatureSupport.getIfFeatures();
   }

   public ValidatorResult addIfFeature(IfFeature ifFeature) {
      return this.ifFeatureSupport.addIfFeature(ifFeature);
   }

   public void setIfFeatures(List<IfFeature> ifFeatures) {
      this.ifFeatureSupport.setIfFeatures(ifFeatures);
   }

   public boolean evaluateFeatures() {
      return this.ifFeatureSupport.evaluateFeatures();
   }

   public List<YangStatement> getEffectiveSubStatements() {
      List<YangStatement> statements = new ArrayList();
      if (this.position != null) {
         statements.add(this.position);
      } else {
         Type type = (Type)this.getParentStatement();
         Bits bits = (Bits)type.getRestriction();
         Long actualVal = bits.getBitActualPosition(this.getArgStr());

         try {
            Position newPos = (Position) YangStatementParserRegister.getInstance().getStatementParserPolicy(YangBuiltinKeyword.POSITION.getQName()).getClazz().getConstructor(String.class).newInstance(actualVal.toString());
            newPos.setContext(this.getContext());
            newPos.setElementPosition(this.getElementPosition());
            statements.add(newPos);
         } catch (InstantiationException var6) {
            var6.printStackTrace();
         } catch (IllegalAccessException var7) {
            var7.printStackTrace();
         } catch (InvocationTargetException var8) {
            var8.printStackTrace();
         } catch (NoSuchMethodException var9) {
            var9.printStackTrace();
         }
      }

      statements.addAll(this.ifFeatureSupport.getIfFeatures());
      statements.addAll(super.getEffectiveSubStatements());
      return statements;
   }
}
