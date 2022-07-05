package org.yangcentral.yangkit.model.impl.stmt;

import org.yangcentral.yangkit.base.ErrorCode;
import org.yangcentral.yangkit.base.Position;
import org.yangcentral.yangkit.base.YangBuiltinKeyword;
import org.yangcentral.yangkit.common.api.QName;
import org.yangcentral.yangkit.common.api.exception.ErrorMessage;
import org.yangcentral.yangkit.common.api.exception.ErrorTag;
import org.yangcentral.yangkit.common.api.exception.Severity;
import org.yangcentral.yangkit.common.api.validate.ValidatorRecordBuilder;
import org.yangcentral.yangkit.common.api.validate.ValidatorResult;
import org.yangcentral.yangkit.common.api.validate.ValidatorResultBuilder;
import org.yangcentral.yangkit.model.api.stmt.Config;
import org.yangcentral.yangkit.model.api.stmt.YangStatement;

public class ConfigImpl extends YangSimpleStatementImpl implements Config {
   public ConfigImpl(String argStr) {
      super(argStr);
   }

   public boolean isConfig() {
      return Boolean.valueOf(this.getArgStr());
   }

   public QName getYangKeyword() {
      return YangBuiltinKeyword.CONFIG.getQName();
   }

   protected ValidatorResult initSelf() {
      ValidatorResultBuilder validatorResultBuilder = new ValidatorResultBuilder();
      validatorResultBuilder.merge(super.initSelf());
      if (!this.getArgStr().equals("true") && !this.getArgStr().equals("false")) {
         ValidatorRecordBuilder<Position, YangStatement> validatorRecordBuilder = new ValidatorRecordBuilder();
         validatorRecordBuilder.setErrorTag(ErrorTag.BAD_ELEMENT);
         validatorRecordBuilder.setSeverity(Severity.ERROR);
         validatorRecordBuilder.setErrorPath(this.getElementPosition());
         validatorRecordBuilder.setBadElement(this);
         validatorRecordBuilder.setErrorMessage(new ErrorMessage(ErrorCode.INVALID_ARG.getFieldName()));
         validatorResultBuilder.addRecord(validatorRecordBuilder.build());
      }

      ValidatorResult result = validatorResultBuilder.build();
      return result;
   }
}
