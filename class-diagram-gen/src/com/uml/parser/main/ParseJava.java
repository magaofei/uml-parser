package com.uml.parser.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import com.github.javaparser.ast.body.*;
import com.uml.parser.enums.Modifiers;
import com.uml.parser.model.UMLClass;
import com.uml.parser.model.UMLMethod;
import com.uml.parser.model.UMLVariable;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

/**
 * Parses Java classes to create {@link UMLClass} for each input class.
 * Deals with variables, constructors and methods.
 * Also, calls {@link Counselor} for creating relationships if any
 * @author rishi
 *
 */
public class ParseJava {
	private Counselor counselor;
	
	/**
	 * Constructor for class
	 */
	public ParseJava() {
		counselor = Counselor.getInstance();
	}
	
	/**
	 * Parsing begins here for each input file
	 * @param files
	 */
	public void parseFiles(List<File> files){
		try{
			for(File file : files){
				System.out.println("Parsing " + file.getAbsolutePath() + " file...");
				CompilationUnit compliationUnit = JavaParser.parse(file);
				createUMLClass(compliationUnit);
			}
			//counselor.removeUnneccessaryMethods();
		}catch(FileNotFoundException ex){
			System.err.println("Error: File not found. Trace: "+ ex.getMessage());
		}catch(IOException ex){
			System.err.println("Error: IO Exception. Trace: "+ ex.getMessage());
		}catch(ParseException ex){
			System.err.println("Error: Parse exception. Trace: "+ ex.getMessage());
		}
	}
	
	/**
	 * Creates {@link UMLClass} for input Java Class.
	 * @param compliationUnit
	 */
	private void createUMLClass(CompilationUnit compliationUnit){
		List<TypeDeclaration> types = compliationUnit.getTypes();
		if (types == null) {
			return;
		}
		for(TypeDeclaration type : types){
			List<BodyDeclaration> bodyDeclarations = type.getMembers();

			if (!(type instanceof ClassOrInterfaceDeclaration)) {

				// TODO enum
				continue;
			}
			boolean isInterface = ((ClassOrInterfaceDeclaration) type).isInterface();
			
			UMLClass umlClass = counselor.getUMLClass(type.getName());
			umlClass.setInterface(isInterface);
			
			counselor.checkForRelatives(umlClass, type);
			
			for(BodyDeclaration body : bodyDeclarations){
				if(body instanceof FieldDeclaration){
					createUMLVariables(umlClass, (FieldDeclaration) body);
				}else if(body instanceof MethodDeclaration){
					createUMLMethods(umlClass, (MethodDeclaration) body, false);
				}else if(body instanceof ConstructorDeclaration){
					createUMLMethods(umlClass, (ConstructorDeclaration) body, true);
				}
			}
			counselor.addUMLClass(umlClass);
		}
	}
	
	/**
	 * All instance variables are parsed here
	 * @param umlClass
	 * @param field
	 */
	private void createUMLVariables(UMLClass umlClass, FieldDeclaration field){
		List<VariableDeclarator> variables = field.getVariables();
		for(VariableDeclarator variable : variables){
			UMLVariable umlVariable = new UMLVariable();
			umlVariable.setModifier(field.getModifiers());
			umlVariable.setName(variable.getId().getName());
			umlVariable.setInitialValue(variable.getInit() == null ? "" : " = " + variable.getInit().toString());
			umlVariable.setUMLClassType(UMLHelper.isUMLClassType(field.getType()));
			umlVariable.setType(field.getType());
			umlClass.getUMLVariables().add(umlVariable);
			counselor.checkForRelatives(umlClass, umlVariable);
		}
	}
	
	/**
	 * All the methods including constructors are parsed here
	 * @param umlClass
	 * @param body
	 * @param isConstructor
	 */
	private void createUMLMethods(UMLClass umlClass, BodyDeclaration body, boolean isConstructor){
		UMLMethod umlMethod = new UMLMethod();
		if(isConstructor){
			ConstructorDeclaration constructor = (ConstructorDeclaration) body;
			umlMethod.setConstructor(true);
			umlMethod.setModifier(constructor.getModifiers());
			umlMethod.setName(constructor.getName());
			umlMethod.setParameters(constructor.getParameters());
			
			parseMethodBody(umlClass, constructor.getBlock());
		}else {
			MethodDeclaration method = (MethodDeclaration) body;
			umlMethod.setConstructor(false);
			umlMethod.setModifier(umlClass.isInterface() ? Modifiers.PUBLIC_ABSTRACT.modifier : method.getModifiers());
			umlMethod.setName(method.getName());
			umlMethod.setParameters(method.getParameters());
			umlMethod.setType(method.getType());
			
			parseMethodBody(umlClass, method.getBody());
		}
		umlClass.getUMLMethods().add(umlMethod);
		counselor.checkForRelatives(umlClass, umlMethod);		
	}
	
	/**
	 * Method body parsing
	 * @param umlClass
	 * @param methodBody
	 */
	private void parseMethodBody(UMLClass umlClass, BlockStmt methodBody){
		if(methodBody == null || methodBody.getStmts() == null){
			return;
		}
		List<Statement> methodStmts = methodBody.getStmts();
		for(Statement statement : methodStmts){
			if(statement instanceof ExpressionStmt && ((ExpressionStmt) statement).getExpression() instanceof VariableDeclarationExpr){
				VariableDeclarationExpr expression = (VariableDeclarationExpr) (((ExpressionStmt) statement).getExpression());
				counselor.checkForRelatives(umlClass, expression);
			}
		}
	}
}