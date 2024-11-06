package com.waterworks.mlqsdummydatagenerator.app.generators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.waterworks.mlqsdummydatagenerator.app.events.EventsService;
import com.waterworks.mlqsdummydatagenerator.app.events.domain.Event;
import com.waterworks.mlqsdummydatagenerator.app.generators.domain.Category;
import com.waterworks.mlqsdummydatagenerator.app.generators.domain.Customer;
import com.waterworks.mlqsdummydatagenerator.app.generators.domain.Employee;
import com.waterworks.mlqsdummydatagenerator.app.generators.domain.Invoice;
import com.waterworks.mlqsdummydatagenerator.app.generators.domain.Order;
import com.waterworks.mlqsdummydatagenerator.app.generators.domain.Product;
import com.waterworks.mlqsdummydatagenerator.app.generators.domain.Supplier;
import com.waterworks.mlqsdummydatagenerator.app.generators.domain.Transaction;
import com.waterworks.mlqsdummydatagenerator.infra.httpout.image.api.RandomImageRepo;
import jakarta.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeneratorRandomEvents {

  private final List<String> names;
  private final List<String> lastname;
  private final List<String> roles;
  private final List<String> fantasySupplierPrefixes;
  private final List<String> fantasyProductNamePrefixes;
  private final List<String> fantasyCategoryPrefixes;
  private final List<String> fantasyProviderNames;
  private final List<String> fantasyProductNames;
  private final List<String> fantasyProductNameSuffixes;
  private final List<String> fantasyProductsDesc;
  private final List<Double> fantasyProductPrices;
  private final List<String> usAddresses;
  private final List<String> fantasyCategories;
  private final List<String> fantasyEmailProviders;
  private final List<String> fantasyOrderStatuses;
  private final List<String> fantasyPaymentMethods;
  private final List<String> fantasyTransactionTypes;
  private final Random random = new Random();
  private final List<Employee> employees;
  private final List<Supplier> suppliers;
  private final List<Category> categories;
  private final List<Product> products;
  private final List<Customer> customers;
  private final List<Order> orders;
  private final List<Invoice> invoices;
  private final List<Transaction> transactions;
  private final ObjectMapper objectMapper;

  private final Map<String, Object> headers = new HashMap<>();
  @Autowired
  private EventsService eventsService;
  @Autowired
  private RandomImageRepo randomImageRepo;

  @Value("${spring.rabbitmq.exchanges.random-events}")
  private List<String> randomEventsEx;

  public void generate() {
    // employees    -> file
    // Products     -> rabbitmq
    // Orders       -> rabbitmq

    generateCategories();
    generateSuppliers();
    generateProducts();

    generateCustomers();
    generateEmployees();
    generateOrderItems();


  }

  ///////////////////// ORDER TRANSACTIONS
  private Transaction generateTransaction(final Invoice invoice) {
    return Transaction.builder()
        .transactionId(UUID.randomUUID().toString())
        .transactionType(getTransactionType())
        .amount(invoice.getTotalAmount())
        .transactionDate(String.valueOf(invoice.getIssueDate()))
        .invoiceId(invoice.getInvoiceId())
        .build();
  }

  private String getTransactionType() {
    return fantasyTransactionTypes.get(random.nextInt(fantasyTransactionTypes.size()));
  }

  ///////////////////// ORDER INVOICES
  private Invoice generateOrderInvoice(final Order order) {
    Invoice invoice = Invoice.builder()
        .invoiceId(UUID.randomUUID().toString())
        .issueDate(String.valueOf(generateRandomDate()))
        .totalAmount(order.getProducts().stream().mapToDouble(Product::getPrice)
            .reduce(0, Double::sum))
        .orderId(order.getOrderId())
        .build();

    if (random.nextInt(2) == 0) {
      Transaction transaction = generateTransaction(invoice);
      transactions.add(transaction);
      sendMessage("payments_system", "Transaction", transaction);
    }
    return invoice;
  }

  ///////////////////// ORDER ITEMS
  private void generateOrderItems() {
    Order order;
    for (int i = 0; i < 2; i++) {
      order = generateOrderItem();
      sendMessage("payments_system", "OrderItem", order);
      orders.add(order);

      if (random.nextInt(2) == 0) {
        Invoice invoice = generateOrderInvoice(order);
        invoices.add(invoice);
        sendMessage("payments_system", "Invoice", invoice);
      }
    }

  }

  private Order generateOrderItem() {
    return Order.builder()
        .orderId(UUID.randomUUID().toString())
        .orderDate(String.valueOf(generateRandomDate()))
        .status(generateRandomOrderStatus())
        .paymentMethod(generateRandomPaymentMethod())
        .customerId(customers.get(random.nextInt(customers.size())).getCustomerId())
        .sellerId(getRandomSeller().getEmployeeId())
        .products(getRandomProductsList())
        .build();
  }

  private LocalDate generateRandomDate() {
    long minDay = LocalDate.of(2015, 1, 1).toEpochDay();
    long maxDay = LocalDate.of(2023, 12, 31).toEpochDay();
    long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
    return LocalDate.ofEpochDay(randomDay);
  }

  private List<Product> getRandomProductsList() {
    final List<Product> productsList = new ArrayList<>();
    for (int i = 0; i < random.nextInt(100); i++) {
      productsList.add(products.get(random.nextInt(products.size())));
    }
    return productsList;
  }

  private String generateRandomOrderStatus() {
    return fantasyOrderStatuses.get(random.nextInt(fantasyOrderStatuses.size()));
  }

  private String generateRandomPaymentMethod() {
    return fantasyPaymentMethods.get(random.nextInt(fantasyPaymentMethods.size()));
  }

  private Employee getRandomSeller() {
    Optional<Employee> seller = employees.stream()
        .filter(employee -> "Sales".equals(employee.getPosition())
            || "Sales Manager".equals(employee.getPosition()))
        .findAny();

    if (seller.isPresent()) {
      return seller.get();
    }
    Employee employee = generateEmployee("Sales");
    employees.add(employee);
    saveEmployee(employee);
    return employee;
  }

  ///////////////////// CUSTOMERS
  private void generateCustomers() {
    Customer customer;
    for (int i = 0; i < 3; i++) {
      customer = generateCustomer();
      sendMessage("product_catalog", "Customer", customer);
      customers.add(customer);
    }
  }

  private Customer generateCustomer() {
    final String name = getRandomName();
    return Customer.builder()
        .customerId(UUID.randomUUID().toString())
        .name(name)
        .address(getRandomAddress())
        .email(name.replace(" ", ".").concat(getRandomEmailProvider()).toLowerCase())
        .phoneNumber(generateRandomPhoneNumber())
        .build();
  }

  private String getRandomEmailProvider() {
    return fantasyEmailProviders.get(random.nextInt(fantasyEmailProviders.size()));
  }


  ///////////////////// PRODUCTS

  private void generateProductImages(final String filepath) {
    byte[] image;
    try {
      image = randomImageRepo.consumeBinaryAPI();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    FileOutputStream fileOutputStream = null;
    try {

      fileOutputStream = new FileOutputStream(filepath);
      fileOutputStream.write(image);
      fileOutputStream.close();


    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  private void generateProducts() {
    Product product;
    for (int i = 0; i < 5; i++) {
      product = generateProduct();
      sendMessage("product_catalog", "Product", product);
      products.add(product);

      final String imageFilepath = "images\\".concat(product.getProductId()).concat(".jpg");
      generateProductImages(imageFilepath);
    }
  }

  private Product generateProduct() {
    return Product.builder()
        .productId(UUID.randomUUID().toString())
        .name(getRandomProductName())
        .description(getRandomProductsDesc())
        .price(getRandomProductsPrice())
        .categoryId(categories.get(random.nextInt(categories.size())).getCategoryId())
        .supplierId(suppliers.get(random.nextInt(suppliers.size())).getSupplierId())
        .build();
  }

  private String getRandomProductName() {
    return fantasyProductNamePrefixes.get(random.nextInt(fantasyProductNamePrefixes.size()))
        .concat(" ")
        .concat(fantasyProductNames.get(random.nextInt(fantasyProductNames.size())))
        .concat(fantasyProductNameSuffixes.get(random.nextInt(fantasyProductNameSuffixes.size())));
  }

  private String getRandomProductsDesc() {
    return fantasyProductsDesc.get(random.nextInt(fantasyProductsDesc.size()));
  }

  private Double getRandomProductsPrice() {
    return fantasyProductPrices.get(random.nextInt(fantasyProductPrices.size()));
  }

  ///////////////////// CATEGORIES
  private void generateCategories() {
    Category category;
    for (int i = 0; i < 2; i++) {
      category = generateCategory();
      sendMessage("product_catalog", "Category", category);
      categories.add(category);
    }
  }

  private Category generateCategory() {
    return Category.builder()
        .categoryId(UUID.randomUUID().toString())
        .name(getRandomCategoryName())
        .build();
  }

  private String getRandomCategoryName() {
    return fantasyCategoryPrefixes.get(random.nextInt(fantasyCategoryPrefixes.size()))
        .concat(" ")
        .concat(fantasyCategories.get(random.nextInt(fantasyCategories.size())));
  }

  ///////////////////// SUPPLIERS

  private void generateSuppliers() {

    Supplier supplier;
    for (int i = 0; i < 5; i++) {
      supplier = generateSupplier();
      sendMessage("product_catalog", "Supplier", supplier);
      suppliers.add(supplier);
    }

  }

  private Supplier generateSupplier() {
    final String name = getRandomSupplierName();
    return Supplier.builder()
        .supplierId(UUID.randomUUID().toString())
        .name(name.replace("-", " "))
        .address(getRandomAddress())
        .email("contact@".concat(name.trim().toLowerCase()).concat(".com"))
        .phoneNumber(generateRandomPhoneNumber())
        .build();
  }

  private String getRandomSupplierName() {
    return fantasySupplierPrefixes.get(random.nextInt(fantasySupplierPrefixes.size()))
        .concat("-")
        .concat(fantasyProviderNames.get(random.nextInt(fantasyProviderNames.size())));
  }

  private String getRandomAddress() {
    return usAddresses.get(random.nextInt(usAddresses.size()));
  }


  ///////////////////// EMPLOYEES
  private void generateEmployees() {
    Employee employeeGenerated;
    for (int i = 0; i < 5; i++) {
      employeeGenerated = generateEmployee();
      employees.add(employeeGenerated);
      saveEmployee(employeeGenerated);
    }
  }

  private void saveEmployee(final Employee employeeGenerated) {
    appendEmployeeToFile(employeeGenerated, "employees.csv"
    );
  }

  private Employee generateEmployee() {
    final String name = getRandomName();
    return Employee.builder()
        .employeeId(UUID.randomUUID().toString())
        .name(name)
        .position(getRandomRole())
        .email(name.replace(" ", ".").concat("@company.com").toLowerCase())
        .phoneNumber(generateRandomPhoneNumber())
        .build();
  }

  private Employee generateEmployee(final String position) {
    final String name = getRandomName();
    return Employee.builder()
        .employeeId(UUID.randomUUID().toString())
        .name(name)
        .position(position)
        .email(name.replace(" ", ".").concat("@company.com").toLowerCase())
        .phoneNumber(generateRandomPhoneNumber())
        .build();
  }

  private String getRandomName() {
    return names.get(random.nextInt(names.size()))
        .concat(" ")
        .concat(lastname.get(random.nextInt(lastname.size())));
  }

  private String getRandomRole() {
    return roles.get(random.nextInt(roles.size()));
  }

  private static void appendEmployeeToFile(Employee employee, String fileName) {
    try (FileWriter fileWriter = new FileWriter(fileName, true)) {
      fileWriter.write(String.join(",",
              employee.getEmployeeId(),
              employee.getName(),
              employee.getPosition(),
              employee.getEmail(),
              employee.getPhoneNumber())
          .concat("\n"));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String generateRandomPhoneNumber() {
    StringBuilder phoneNumber = new StringBuilder();

    // Generar el código de país (2 dígitos)
    phoneNumber.append("+");
    phoneNumber.append(generateRandomNumber(1, 9)); // Asegura que el primer dígito no sea cero
    phoneNumber.append(generateRandomNumber(0, 9));

    // Generar el código de área (3 dígitos)
    phoneNumber.append(" (");
    phoneNumber.append(generateRandomNumber(100, 999));
    phoneNumber.append(") ");

    // Generar el primer bloque de números (3 dígitos)
    phoneNumber.append(generateRandomNumber(100, 999));
    phoneNumber.append("-");

    // Generar el segundo bloque de números (4 dígitos)
    phoneNumber.append(generateRandomNumber(1000, 9999));

    return phoneNumber.toString();
  }

  private static int generateRandomNumber(int min, int max) {
    Random rand = new Random();
    return rand.nextInt((max - min) + 1) + min;
  }

  @PostConstruct
  private void postConstruct() {
    objectMapper.registerModule(new JavaTimeModule());
  }

  public GeneratorRandomEvents() {
    names = new ArrayList<>();
    lastname = new ArrayList<>();
    roles = new ArrayList<>();
    employees = new ArrayList<>();
    fantasySupplierPrefixes = new ArrayList<>();
    fantasyProviderNames = new ArrayList<>();
    usAddresses = new ArrayList<>();
    suppliers = new ArrayList<>();
    fantasyCategories = new ArrayList<>();
    fantasyCategoryPrefixes = new ArrayList<>();
    fantasyProductNamePrefixes = new ArrayList<>();
    fantasyProductNameSuffixes = new ArrayList<>();
    fantasyProductNames = new ArrayList<>();
    fantasyProductsDesc = new ArrayList<>();
    fantasyProductPrices = new ArrayList<>();
    fantasyEmailProviders = new ArrayList<>();
    fantasyOrderStatuses = new ArrayList<>();
    fantasyPaymentMethods = new ArrayList<>();
    fantasyTransactionTypes = new ArrayList<>();
    categories = new ArrayList<>();
    products = new ArrayList<>();
    customers = new ArrayList<>();
    orders = new ArrayList<>();
    invoices = new ArrayList<>();
    transactions = new ArrayList<>();
    objectMapper = new ObjectMapper();
    names.add("Liam");
    names.add("Olivia");
    names.add("Noah");
    names.add("Emma");
    names.add("Oliver");
    names.add("Ava");
    names.add("William");
    names.add("Sophia");
    names.add("Elijah");
    names.add("Isabella");
    names.add("James");
    names.add("Charlotte");
    names.add("Benjamin");
    names.add("Amelia");
    names.add("Lucas");
    names.add("Mia");
    names.add("Henry");
    names.add("Harper");
    names.add("Alexander");
    names.add("Evelyn");
    names.add("Michael");
    names.add("Abigail");
    names.add("Daniel");
    names.add("Emily");
    names.add("Matthew");
    names.add("Elizabeth");
    names.add("Jackson");
    names.add("Sofia");
    names.add("Sebastian");
    names.add("Avery");
    names.add("Aiden");
    names.add("Ella");
    names.add("David");
    names.add("Madison");
    names.add("Joseph");
    names.add("Scarlett");
    names.add("Carter");
    names.add("Victoria");
    names.add("Wyatt");
    names.add("Grace");
    names.add("John");
    names.add("Chloe");
    names.add("Owen");
    names.add("Penelope");
    names.add("Dylan");
    names.add("Luna");
    names.add("Luke");
    names.add("Lily");
    names.add("Gabriel");
    names.add("Zoey");
    names.add("Anthony");
    names.add("Hannah");
    names.add("Isaac");
    names.add("Nora");
    names.add("Grayson");
    names.add("Riley");
    names.add("Jack");
    names.add("Eleanor");
    names.add("Julian");
    names.add("Savannah");
    names.add("Levi");
    names.add("Claire");
    names.add("Christopher");
    names.add("Isla");
    names.add("Joshua");
    names.add("Charlie");
    names.add("Andrew");
    names.add("Skylar");
    names.add("Lincoln");
    names.add("Aria");
    names.add("Mateo");
    names.add("Lucy");
    names.add("Ryan");
    names.add("Anna");
    names.add("Jaxon");
    names.add("Leah");
    names.add("Nathan");
    names.add("Ellie");
    names.add("Aaron");
    names.add("Maya");
    names.add("Isaiah");
    names.add("Valentina");
    names.add("Thomas");
    names.add("Ruby");
    names.add("Charles");
    names.add("Kennedy");
    names.add("Caleb");
    names.add("Lydia");
    names.add("Josiah");
    names.add("Paisley");
    names.add("Christian");
    names.add("Harmony");
    names.add("Hunter");
    names.add("Zoe");
    names.add("Eli");
    names.add("Natalie");
    names.add("Jonathan");
    names.add("Addison");
    names.add("Connor");
    names.add("Lillian");
    names.add("Landon");
    names.add("Layla");
    names.add("Adrian");
    names.add("Brooklyn");
    names.add("Asher");
    names.add("Alexa");
    names.add("Cameron");
    names.add("Audrey");
    names.add("Leo");
    names.add("Aubrey");
    names.add("Theodore");
    names.add("Aaliyah");
    names.add("Jeremiah");
    names.add("Peyton");
    names.add("Hudson");
    names.add("Bella");
    names.add("Robert");
    names.add("Violet");
    names.add("Easton");
    names.add("Stella");
    names.add("Nolan");
    names.add("Hazel");
    names.add("Nicholas");
    names.add("Nova");
    names.add("Ezra");
    names.add("Mackenzie");
    names.add("Colton");
    names.add("Willow");
    names.add("Angel");
    names.add("Ivy");
    names.add("Brayden");
    names.add("Elena");
    names.add("Jordan");
    names.add("Holly");
    names.add("Dominic");
    names.add("Emilia");
    names.add("Austin");
    names.add("Maria");
    names.add("Ian");
    names.add("Everly");
    names.add("Adam");
    names.add("Elise");
    names.add("Elias");
    names.add("Jasmine");
    names.add("Jaxson");
    names.add("Delilah");
    names.add("Greyson");
    names.add("Camilla");
    names.add("Jose");
    names.add("Daisy");
    names.add("Evan");
    names.add("Reagan");
    names.add("Levi");
    names.add("Juliana");
    names.add("Jason");
    names.add("Adeline");
    names.add("Cooper");
    names.add("Haley");
    names.add("Chase");
    names.add("Molly");
    names.add("Kevin");
    names.add("Emery");
    names.add("Parker");
    names.add("Athena");
    names.add("Tyler");
    names.add("Claudia");
    names.add("Ayden");
    names.add("Fiona");
    names.add("Leonardo");
    names.add("Jade");
    names.add("Carter");
    names.add("Keira");
    names.add("Brody");
    names.add("Adalyn");
    names.add("Kayden");
    names.add("Sienna");
    names.add("Roman");
    names.add("Brielle");
    names.add("Brandon");
    names.add("Aliyah");
    names.add("Ashley");
    names.add("Gianna");
    names.add("Thomas");
    names.add("Isabelle");
    names.add("Carson");
    names.add("Rylee");
    names.add("Cooper");
    names.add("Tessa");
    names.add("Xavier");
    names.add("Alana");
    names.add("Paxton");
    names.add("Summer");
    names.add("Michael");
    names.add("Isabel");
    names.add("Kai");
    names.add("Raelynn");
    names.add("Dylan");
    names.add("Lyric");
    names.add("Jace");
    names.add("Leilani");
    names.add("Austin");
    names.add("Finley");
    names.add("Jose");
    names.add("Diana");
    names.add("Adam");
    names.add("Willow");
    names.add("Ayden");
    names.add("Melanie");
    names.add("Santiago");
    names.add("Lila");
    names.add("Jordan");
    names.add("Gabriella");
    names.add("Dominic");
    names.add("Isabelle");
    names.add("Ian");
    names.add("Aurora");
    names.add("Josiah");
    names.add("Jocelyn");
    names.add("Colton");
    names.add("Amaya");
    names.add("Gavin");
    names.add("Dakota");
    names.add("Bentley");
    names.add("Miranda");
    names.add("Jason");
    names.add("Juliet");
    names.add("Brody");
    names.add("Leila");
    names.add("Axel");
    names.add("Gracie");
    names.add("Jesus");
    names.add("Kiara");
    names.add("Miles");
    names.add("Lena");
    names.add("Eric");
    names.add("Londyn");
    names.add("Micah");
    names.add("Ryleigh");
    names.add("Silas");
    names.add("Hope");
    names.add("King");
    names.add("Harper");
    names.add("Luis");
    names.add("Hayden");
    names.add("Diego");
    names.add("Iris");
    names.add("Jayden");
    names.add("Alaina");
    names.add("Bennett");
    names.add("Sara");
    names.add("Giovanni");
    names.add("Raegan");
    names.add("Timothy");
    names.add("Michelle");
    names.add("Steven");
    names.add("Mariah");
    names.add("Edward");
    names.add("Annabelle");
    names.add("Jayce");
    names.add("Skyla");
    names.add("Preston");
    names.add("Kaitlyn");
    names.add("Wesley");
    names.add("Celeste");
    names.add("Richard");
    names.add("Daniela");
    names.add("Emmanuel");
    names.add("Izabella");
    names.add("Zayden");
    names.add("Kylee");

    lastname.add("Smith");
    lastname.add("Johnson");
    lastname.add("Williams");
    lastname.add("Brown");
    lastname.add("Jones");
    lastname.add("Miller");
    lastname.add("Davis");
    lastname.add("Garcia");
    lastname.add("Rodriguez");
    lastname.add("Martinez");
    lastname.add("Hernandez");
    lastname.add("Lopez");
    lastname.add("Gonzalez");
    lastname.add("Wilson");
    lastname.add("Anderson");
    lastname.add("Thomas");
    lastname.add("Taylor");
    lastname.add("Moore");
    lastname.add("Jackson");
    lastname.add("Martin");
    lastname.add("Lee");
    lastname.add("Perez");
    lastname.add("Thompson");
    lastname.add("White");
    lastname.add("Harris");
    lastname.add("Sanchez");
    lastname.add("Clark");
    lastname.add("Ramirez");
    lastname.add("Lewis");
    lastname.add("Robinson");
    lastname.add("Walker");
    lastname.add("Young");
    lastname.add("Allen");
    lastname.add("King");
    lastname.add("Wright");
    lastname.add("Scott");
    lastname.add("Torres");
    lastname.add("Nguyen");
    lastname.add("Hill");
    lastname.add("Flores");
    lastname.add("Green");
    lastname.add("Adams");
    lastname.add("Nelson");
    lastname.add("Baker");
    lastname.add("Hall");
    lastname.add("Rivera");
    lastname.add("Campbell");
    lastname.add("Mitchell");
    lastname.add("Carter");
    lastname.add("Roberts");
    lastname.add("Gomez");
    lastname.add("Phillips");
    lastname.add("Evans");
    lastname.add("Turner");
    lastname.add("Diaz");
    lastname.add("Parker");
    lastname.add("Cruz");
    lastname.add("Edwards");
    lastname.add("Collins");
    lastname.add("Reyes");
    lastname.add("Stewart");
    lastname.add("Morris");
    lastname.add("Morales");
    lastname.add("Murphy");
    lastname.add("Cook");
    lastname.add("Rogers");
    lastname.add("Gutierrez");
    lastname.add("Ortiz");
    lastname.add("Morgan");
    lastname.add("Cooper");
    lastname.add("Peterson");
    lastname.add("Bailey");
    lastname.add("Reed");
    lastname.add("Kelly");
    lastname.add("Howard");
    lastname.add("Ramos");
    lastname.add("Kim");
    lastname.add("Cox");
    lastname.add("Ward");
    lastname.add("Richardson");
    lastname.add("Watson");
    lastname.add("Brooks");
    lastname.add("Chavez");
    lastname.add("Wood");
    lastname.add("James");
    lastname.add("Bennett");
    lastname.add("Gray");
    lastname.add("Mendoza");
    lastname.add("Ruiz");
    lastname.add("Hughes");
    lastname.add("Price");
    lastname.add("Alvarez");
    lastname.add("Castillo");
    lastname.add("Sanders");
    lastname.add("Patel");
    lastname.add("Myers");
    lastname.add("Long");
    lastname.add("Ross");
    lastname.add("Foster");
    lastname.add("Jimenez");
    lastname.add("Powell");
    lastname.add("Jenkins");
    lastname.add("Perry");
    lastname.add("Russell");
    lastname.add("Sullivan");
    lastname.add("Bell");
    lastname.add("Coleman");
    lastname.add("Butler");
    lastname.add("Henderson");
    lastname.add("Barnes");
    lastname.add("Gonzales");
    lastname.add("Fisher");
    lastname.add("Vasquez");
    lastname.add("Simmons");
    lastname.add("Romero");
    lastname.add("Jordan");
    lastname.add("Patterson");
    lastname.add("Alexander");
    lastname.add("Hamilton");
    lastname.add("Graham");
    lastname.add("Reynolds");
    lastname.add("Griffin");
    lastname.add("Wallace");
    lastname.add("Moreno");
    lastname.add("West");
    lastname.add("Cole");
    lastname.add("Hayes");
    lastname.add("Bryant");
    lastname.add("Herrera");
    lastname.add("Gibson");
    lastname.add("Ellis");
    lastname.add("Tran");
    lastname.add("Medina");
    lastname.add("Aguilar");
    lastname.add("Stevens");
    lastname.add("Murray");
    lastname.add("Ford");
    lastname.add("Castro");
    lastname.add("Marshall");
    lastname.add("Owens");
    lastname.add("Harrison");
    lastname.add("Fernandez");
    lastname.add("Mcdonald");
    lastname.add("Woods");
    lastname.add("Washington");
    lastname.add("Kennedy");
    lastname.add("Wells");
    lastname.add("Vargas");
    lastname.add("Henry");
    lastname.add("Chen");
    lastname.add("Freeman");
    lastname.add("Webb");
    lastname.add("Tucker");
    lastname.add("Guzman");
    lastname.add("Burns");
    lastname.add("Crawford");
    lastname.add("Olson");
    lastname.add("Simpson");
    lastname.add("Porter");
    lastname.add("Hunter");
    lastname.add("Gordon");
    lastname.add("Mendez");
    lastname.add("Silva");
    lastname.add("Shaw");
    lastname.add("Snyder");
    lastname.add("Mason");
    lastname.add("Dixon");
    lastname.add("Munoz");
    lastname.add("Hunt");
    lastname.add("Hicks");
    lastname.add("Holmes");
    lastname.add("Palmer");
    lastname.add("Wagner");
    lastname.add("Black");
    lastname.add("Robertson");
    lastname.add("Boyd");
    lastname.add("Rose");
    lastname.add("Stone");
    lastname.add("Salazar");
    lastname.add("Fox");
    lastname.add("Warren");
    lastname.add("Mills");
    lastname.add("Meyer");
    lastname.add("Rice");
    lastname.add("Schmidt");
    lastname.add("Garza");
    lastname.add("Daniels");
    lastname.add("Ferguson");
    lastname.add("Nichols");
    lastname.add("Stephens");
    lastname.add("Soto");
    lastname.add("Weaver");
    lastname.add("Ryan");
    lastname.add("Gardner");
    lastname.add("Payne");
    lastname.add("Grant");
    lastname.add("Dunn");
    lastname.add("Kelley");
    lastname.add("Spencer");
    lastname.add("Hawkins");
    lastname.add("Arnold");
    lastname.add("Pierce");
    lastname.add("Vazquez");
    lastname.add("Hansen");
    lastname.add("Peters");
    lastname.add("Santos");
    lastname.add("Hart");
    lastname.add("Bradley");
    lastname.add("Knight");
    lastname.add("Elliott");
    lastname.add("Cross");
    lastname.add("Gomez");
    lastname.add("Lawrence");
    lastname.add("Gutierrez");
    lastname.add("Diaz");
    lastname.add("Haynes");
    lastname.add("Hubbard");
    lastname.add("Finley");
    lastname.add("Gibbs");
    lastname.add("West");
    lastname.add("Doyle");
    lastname.add("Wright");
    lastname.add("Kim");
    lastname.add("Sullivan");
    lastname.add("Yates");
    lastname.add("Luna");
    lastname.add("Fowler");
    lastname.add("Lopez");
    lastname.add("Zimmerman");
    lastname.add("Walsh");
    lastname.add("Campbell");
    lastname.add("Wagner");
    lastname.add("Maldonado");
    lastname.add("Long");
    lastname.add("Singleton");
    lastname.add("Banks");
    lastname.add("Brown");
    lastname.add("Browning");
    lastname.add("Winters");
    lastname.add("Kennedy");
    lastname.add("Mcgee");
    lastname.add("Velez");
    lastname.add("Farrell");
    lastname.add("Summers");
    lastname.add("Snyder");
    lastname.add("Holt");
    lastname.add("Mercado");
    lastname.add("Bridges");
    lastname.add("Everett");
    lastname.add("Barton");
    lastname.add("Powers");
    lastname.add("Arroyo");
    lastname.add("Friedman");
    lastname.add("David");
    lastname.add("Patterson");
    lastname.add("Green");
    lastname.add("Fields");
    lastname.add("Blackburn");
    lastname.add("Ross");
    lastname.add("Stanley");
    lastname.add("Ray");
    lastname.add("Pace");
    lastname.add("Young");
    lastname.add("Randolph");
    lastname.add("Mccarthy");
    lastname.add("Davidson");
    lastname.add("Lara");
    lastname.add("Armstrong");
    lastname.add("Contreras");
    lastname.add("Hernandez");
    lastname.add("Chan");
    lastname.add("Carr");
    lastname.add("Ayala");
    lastname.add("Holloway");
    lastname.add("Roth");
    lastname.add("Nieves");
    lastname.add("Sosa");
    lastname.add("Oconnell");
    lastname.add("Dotson");
    lastname.add("May");
    lastname.add("Obrien");
    lastname.add("Mercer");
    lastname.add("Barr");
    lastname.add("Skinner");
    lastname.add("Estes");
    lastname.add("Rosa");
    lastname.add("Ellison");
    lastname.add("Wilkinson");
    lastname.add("Rojas");
    lastname.add("Sampson");
    lastname.add("Sweeney");
    lastname.add("Norris");
    lastname.add("Obrien");
    lastname.add("Cabrera");
    lastname.add("Gould");
    lastname.add("Harrison");
    lastname.add("Rodgers");
    lastname.add("Parsons");
    lastname.add("Stafford");
    lastname.add("Klein");
    lastname.add("Keller");
    lastname.add("Wagner");
    lastname.add("Pacheco");
    lastname.add("Bowen");
    lastname.add("Sharp");
    lastname.add("Barber");
    lastname.add("Hartman");
    lastname.add("Hickman");
    lastname.add("Nash");
    lastname.add("Poole");
    lastname.add("Nicholson");
    lastname.add("Morton");
    lastname.add("Francis");
    lastname.add("Browning");
    lastname.add("Rowe");
    lastname.add("Goodwin");
    lastname.add("Watts");
    lastname.add("Gill");
    lastname.add("Lowe");
    lastname.add("Bautista");
    lastname.add("Lambert");
    lastname.add("Shah");
    lastname.add("Espinoza");
    lastname.add("Juarez");
    lastname.add("Hines");
    lastname.add("Reeves");
    lastname.add("Warner");
    lastname.add("Horton");
    lastname.add("Vargas");
    lastname.add("Stevenson");
    lastname.add("Callahan");
    lastname.add("Wiggins");
    lastname.add("Underwood");
    lastname.add("Leach");
    lastname.add("Gentry");
    lastname.add("Hess");
    lastname.add("Duran");
    lastname.add("Wade");
    lastname.add("Novak");
    lastname.add("Ponce");
    lastname.add("Barajas");

    roles.add("CEO (Chief Executive Officer)");
    roles.add("COO (Chief Operating Officer)");
    roles.add("CFO (Chief Financial Officer)");
    roles.add("CTO (Chief Technology Officer)");
    roles.add("CHRO (Chief Human Resources Officer)");
    roles.add("Marketing Director");
    roles.add("Sales Director");
    roles.add("Product Development Director");
    roles.add("Project Manager");
    roles.add("HR Manager (Human Resources Manager)");
    roles.add("Finance Manager");
    roles.add("Digital Marketing Manager");
    roles.add("Sales Manager");
    roles.add("Production Manager");
    roles.add("Logistics Manager");
    roles.add("Quality Manager");
    roles.add("Business Development Manager");
    roles.add("Operations Manager");
    roles.add("Customer Service Manager");
    roles.add("Data Analyst");
    roles.add("Financial Analyst");
    roles.add("Marketing Analyst");
    roles.add("Systems Analyst");
    roles.add("HR Analyst (Human Resources Analyst)");
    roles.add("Software Engineer");
    roles.add("Sales Representative");
    roles.add("Customer Service Representative");
    roles.add("Accountant");
    roles.add("Marketing Coordinator");
    roles.add("Operations Coordinator");
    roles.add("HR Coordinator (Human Resources Coordinator)");
    roles.add("Business Analyst");
    roles.add("IT Support Specialist");
    roles.add("Recruiter");
    roles.add("Content Writer");
    roles.add("Graphic Designer");
    roles.add("Logistics Coordinator");
    roles.add("Quality Assurance Specialist");
    roles.add("Legal Counsel");
    roles.add("Executive Assistant");
    roles.add("Office Manager");
    roles.add("Administrative Assistant");
    roles.add("Procurement Manager");
    roles.add("Supply Chain Manager");
    roles.add("Training Manager");
    roles.add("Safety Officer");
    roles.add("Risk Analyst");
    roles.add("Brand Manager");
    roles.add("Sales");

    fantasySupplierPrefixes.add("Golden");
    fantasySupplierPrefixes.add("Silver");
    fantasySupplierPrefixes.add("Diamond");
    fantasySupplierPrefixes.add("Crystal");
    fantasySupplierPrefixes.add("Magic");
    fantasySupplierPrefixes.add("Mystic");
    fantasySupplierPrefixes.add("Royal");
    fantasySupplierPrefixes.add("Celestial");
    fantasySupplierPrefixes.add("Eternal");
    fantasySupplierPrefixes.add("Dream");
    fantasySupplierPrefixes.add("Star");
    fantasySupplierPrefixes.add("Cosmic");
    fantasySupplierPrefixes.add("Divine");
    fantasySupplierPrefixes.add("Enchanted");
    fantasySupplierPrefixes.add("Wonder");
    fantasySupplierPrefixes.add("Mythical");
    fantasySupplierPrefixes.add("Fairy");
    fantasySupplierPrefixes.add("Astral");
    fantasySupplierPrefixes.add("Elysium");
    fantasySupplierPrefixes.add("Realm");
    fantasySupplierPrefixes.add("Epic");
    fantasySupplierPrefixes.add("Sorcery");
    fantasySupplierPrefixes.add("Fabled");
    fantasySupplierPrefixes.add("Wizard");
    fantasySupplierPrefixes.add("Phoenix");
    fantasySupplierPrefixes.add("Dragon");
    fantasySupplierPrefixes.add("Titanic");
    fantasySupplierPrefixes.add("Oracle");
    fantasySupplierPrefixes.add("Miracle");
    fantasySupplierPrefixes.add("Legend");
    fantasySupplierPrefixes.add("Enigma");
    fantasySupplierPrefixes.add("Frostbite");
    fantasySupplierPrefixes.add("Pandora's");
    fantasySupplierPrefixes.add("Spellbound");
    fantasySupplierPrefixes.add("Majestic");
    fantasySupplierPrefixes.add("Genesis");
    fantasySupplierPrefixes.add("Eclipse");
    fantasySupplierPrefixes.add("Whispering");
    fantasySupplierPrefixes.add("Phantom");
    fantasySupplierPrefixes.add("Spiritual");
    fantasySupplierPrefixes.add("Radiant");
    fantasySupplierPrefixes.add("Whirlwind");
    fantasySupplierPrefixes.add("Arcane");
    fantasySupplierPrefixes.add("Thunderbolt");
    fantasySupplierPrefixes.add("Harmony");
    fantasySupplierPrefixes.add("Sapphire");
    fantasySupplierPrefixes.add("Zenith");
    fantasySupplierPrefixes.add("Aurora");
    fantasySupplierPrefixes.add("Warp");

    fantasyProviderNames.add("Starlight Suppliers");
    fantasyProviderNames.add("Eternal Ventures");
    fantasyProviderNames.add("Nebula Trading Co.");
    fantasyProviderNames.add("Dreamscape Distributors");
    fantasyProviderNames.add("Galactic Goods Inc.");
    fantasyProviderNames.add("Whimsy Wholesalers");
    fantasyProviderNames.add("Mystic Merchants");
    fantasyProviderNames.add("Celestial Connections");
    fantasyProviderNames.add("Infinity Imports");
    fantasyProviderNames.add("Enchanted Enterprises");
    fantasyProviderNames.add("Wonderland Wholesales");
    fantasyProviderNames.add("Mythical Markets");
    fantasyProviderNames.add("Fairyland Foods");
    fantasyProviderNames.add("Magic Mart");
    fantasyProviderNames.add("Astral Supplies");
    fantasyProviderNames.add("Elysium Emporium");
    fantasyProviderNames.add("Realm Resources");
    fantasyProviderNames.add("Epic Enterprises");
    fantasyProviderNames.add("Sorcery Suppliers");
    fantasyProviderNames.add("Fabled Foods");
    fantasyProviderNames.add("Wizardry Warehouse");
    fantasyProviderNames.add("Phoenix Products");
    fantasyProviderNames.add("Dragon Distributors");
    fantasyProviderNames.add("Titanic Traders");
    fantasyProviderNames.add("Oracle Outfitters");
    fantasyProviderNames.add("Mystical Merchandise");
    fantasyProviderNames.add("Miracle Markets");
    fantasyProviderNames.add("Legend Ltd.");
    fantasyProviderNames.add("Enigma Enterprises");
    fantasyProviderNames.add("Frostbite Foods");
    fantasyProviderNames.add("Pandora's Products");
    fantasyProviderNames.add("Spellbound Supplies");
    fantasyProviderNames.add("Majestic Mart");
    fantasyProviderNames.add("Genesis Goods");
    fantasyProviderNames.add("Eclipse Exports");
    fantasyProviderNames.add("Whispering Wholesales");
    fantasyProviderNames.add("Phantom Providers");
    fantasyProviderNames.add("Spiritual Suppliers");
    fantasyProviderNames.add("Radiant Resources");
    fantasyProviderNames.add("Whirlwind Wholesalers");
    fantasyProviderNames.add("Arcane Associates");
    fantasyProviderNames.add("Thunderbolt Traders");
    fantasyProviderNames.add("Harmony Holdings");
    fantasyProviderNames.add("Sapphire Suppliers");
    fantasyProviderNames.add("Zenith Enterprises");
    fantasyProviderNames.add("Aurora Assets");
    fantasyProviderNames.add("Warp Works");

    usAddresses.add("123 Main St, Anytown, NY 12345");
    usAddresses.add("456 Elm St, Somewhereville, CA 67890");
    usAddresses.add("789 Oak St, Nowhere City, TX 13579");
    usAddresses.add("321 Pine St, Anywhere, FL 24680");
    usAddresses.add("987 Maple St, Nowheresville, AZ 97531");
    usAddresses.add("234 Cedar St, Everytown, GA 86420");
    usAddresses.add("567 Birch St, Elsewhere, WA 75309");
    usAddresses.add("890 Walnut St, Noway, OR 46801");
    usAddresses.add("654 Spruce St, Nowhither, HI 35780");
    usAddresses.add("321 Ash St, Someplace, IL 90210");
    usAddresses.add("876 Willow St, Nowhence, MI 54632");
    usAddresses.add("543 Poplar St, Somewhither, NC 78901");
    usAddresses.add("210 Fir St, Anywhither, VA 23456");
    usAddresses.add("987 Pine St, Someways, CO 01234");
    usAddresses.add("654 Cedar St, Nowhenceville, NJ 56789");
    usAddresses.add("321 Birch St, Elsewhither, MA 89012");
    usAddresses.add("876 Elm St, Anyways, WA 34567");
    usAddresses.add("543 Oak St, Nowise, AZ 67890");
    usAddresses.add("210 Maple St, Somehence, TX 12345");
    usAddresses.add("987 Willow St, Anywise, CA 45678");
    usAddresses.add("123 Sycamore St, Nowhence, NM 34567");
    usAddresses.add("456 Walnut St, Anywiseville, PA 89012");
    usAddresses.add("789 Cedar St, Nowheresburg, SC 23456");
    usAddresses.add("321 Elm St, Elsewheresville, TN 56789");
    usAddresses.add("987 Maple St, Anywheresville, KY 01234");
    usAddresses.add("654 Oak St, Somewheresville, OH 34567");
    usAddresses.add("321 Pine St, Nowheresville, WA 67890");
    usAddresses.add("876 Birch St, Anywaysburg, FL 12345");
    usAddresses.add("543 Sycamore St, Nowhereville, GA 45678");
    usAddresses.add("210 Elm St, Anywheresburg, CO 78901");
    usAddresses.add("987 Cedar St, Anywhereville, UT 23456");
    usAddresses.add("654 Walnut St, Anytown, NY 56789");
    usAddresses.add("321 Oak St, Elsewhereville, TX 01234");
    usAddresses.add("876 Maple St, Nowhereville, CA 34567");
    usAddresses.add("543 Pine St, Anytownville, WA 67890");
    usAddresses.add("210 Birch St, Nowheresburg, AZ 12345");
    usAddresses.add("987 Sycamore St, Anywheresville, FL 45678");
    usAddresses.add("654 Cedar St, Anywhere, OR 78901");
    usAddresses.add("321 Elm St, Nowheresville, MN 23456");
    usAddresses.add("876 Pine St, Anyplace, WA 56789");
    usAddresses.add("543 Oak St, Nowhere, TX 01234");
    usAddresses.add("210 Maple St, Anytown, IL 34567");
    usAddresses.add("987 Elm St, Elsewhere, PA 67890");
    usAddresses.add("654 Pine St, Anytown, WA 12345");
    usAddresses.add("321 Birch St, Somewhere, CA 45678");
    usAddresses.add("876 Oak St, Anyplace, FL 78901");
    usAddresses.add("543 Maple St, Anytown, GA 23456");
    usAddresses.add("210 Cedar St, Anywhere, AZ 56789");
    usAddresses.add("987 Elm St, Somewhere, WA 01234");
    usAddresses.add("654 Pine St, Anyplace, TX 34567");
    usAddresses.add("321 Birch St, Nowhere, IL 67890");
    usAddresses.add("876 Oak St, Anytown, PA 12345");
    usAddresses.add("543 Maple St, Somewhere, MN 45678");
    usAddresses.add("210 Cedar St, Anyplace, WA 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Somewhere, FL 56789");
    usAddresses.add("321 Birch St, Anywhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");
    usAddresses.add("543 Maple St, Anytown, WA 67890");
    usAddresses.add("210 Cedar St, Somewhere, TX 12345");
    usAddresses.add("987 Elm St, Anywhere, IL 45678");
    usAddresses.add("654 Pine St, Anyplace, PA 78901");
    usAddresses.add("321 Birch St, Anytown, MN 23456");
    usAddresses.add("876 Oak St, Somewhere, WA 56789");
    usAddresses.add("543 Maple St, Anywhere, TX 01234");
    usAddresses.add("210 Cedar St, Somewhere, CA 34567");
    usAddresses.add("987 Elm St, Anyplace, FL 67890");
    usAddresses.add("654 Pine St, Anytown, GA 12345");
    usAddresses.add("321 Birch St, Somewhere, AZ 45678");
    usAddresses.add("876 Oak St, Anyplace, WA 78901");
    usAddresses.add("543 Maple St, Anytown, PA 23456");
    usAddresses.add("210 Cedar St, Somewhere, NY 56789");
    usAddresses.add("987 Elm St, Anyplace, TX 01234");
    usAddresses.add("654 Pine St, Anywhere, IL 34567");
    usAddresses.add("321 Birch St, Somewhere, PA 67890");
    usAddresses.add("876 Oak St, Anytown, MN 12345");
    usAddresses.add("543 Maple St, Somewhere, WA 45678");
    usAddresses.add("210 Cedar St, Anyplace, TX 78901");
    usAddresses.add("987 Elm St, Anytown, CA 23456");
    usAddresses.add("654 Pine St, Anywhere, FL 56789");
    usAddresses.add("321 Birch St, Somewhere, GA 01234");
    usAddresses.add("876 Oak St, Anyplace, AZ 34567");

    fantasyCategories.add("Magic Swords");
    fantasyCategories.add("Healing Potions");
    fantasyCategories.add("Dragons");
    fantasyCategories.add("Elves");
    fantasyCategories.add("Fairies");
    fantasyCategories.add("Magic Wands");
    fantasyCategories.add("Enchanted Armor");
    fantasyCategories.add("Wizard Robes");
    fantasyCategories.add("Magical Creatures");
    fantasyCategories.add("Spell Books");
    fantasyCategories.add("Crystal Balls");
    fantasyCategories.add("Amulets");
    fantasyCategories.add("Mermaids");
    fantasyCategories.add("Goblins");
    fantasyCategories.add("Unicorns");
    fantasyCategories.add("Phoenixes");
    fantasyCategories.add("Centaur");
    fantasyCategories.add("Gargoyles");
    fantasyCategories.add("Witch Hats");
    fantasyCategories.add("Cauldrons");
    fantasyCategories.add("Brooms");
    fantasyCategories.add("Flying Carpets");
    fantasyCategories.add("Trolls");
    fantasyCategories.add("Fairy Dust");
    fantasyCategories.add("Enchanted Forests");
    fantasyCategories.add("Magic Beans");
    fantasyCategories.add("Genies");
    fantasyCategories.add("Hobbits");
    fantasyCategories.add("Chimeras");
    fantasyCategories.add("Griffins");
    fantasyCategories.add("Dwarves");
    fantasyCategories.add("Leprechauns");
    fantasyCategories.add("Orcs");
    fantasyCategories.add("Magic Mirrors");
    fantasyCategories.add("Wishing Wells");
    fantasyCategories.add("Centaurs");
    fantasyCategories.add("Sphinxes");
    fantasyCategories.add("Pegasus");
    fantasyCategories.add("Fairy Rings");
    fantasyCategories.add("Thunderbirds");
    fantasyCategories.add("Werewolves");
    fantasyCategories.add("Vampires");
    fantasyCategories.add("Zombies");
    fantasyCategories.add("Skeletons");

    fantasyCategoryPrefixes.add("Mystic");
    fantasyCategoryPrefixes.add("Enchanted");
    fantasyCategoryPrefixes.add("Celestial");
    fantasyCategoryPrefixes.add("Arcane");
    fantasyCategoryPrefixes.add("Ethereal");
    fantasyCategoryPrefixes.add("Shadow");
    fantasyCategoryPrefixes.add("Divine");
    fantasyCategoryPrefixes.add("Astral");
    fantasyCategoryPrefixes.add("Radiant");
    fantasyCategoryPrefixes.add("Cosmic");
    fantasyCategoryPrefixes.add("Eternal");
    fantasyCategoryPrefixes.add("Dream");
    fantasyCategoryPrefixes.add("Ancient");
    fantasyCategoryPrefixes.add("Lunar");
    fantasyCategoryPrefixes.add("Crystal");
    fantasyCategoryPrefixes.add("Starlight");
    fantasyCategoryPrefixes.add("Faerie");
    fantasyCategoryPrefixes.add("Phoenix");
    fantasyCategoryPrefixes.add("Twilight");
    fantasyCategoryPrefixes.add("Elemental");
    fantasyCategoryPrefixes.add("Noble");
    fantasyCategoryPrefixes.add("Golden");
    fantasyCategoryPrefixes.add("Silver");
    fantasyCategoryPrefixes.add("Lunar");
    fantasyCategoryPrefixes.add("Sunlit");
    fantasyCategoryPrefixes.add("Thunder");
    fantasyCategoryPrefixes.add("Storm");
    fantasyCategoryPrefixes.add("Shining");
    fantasyCategoryPrefixes.add("Moonlit");
    fantasyCategoryPrefixes.add("Dawn");
    fantasyCategoryPrefixes.add("Dusk");
    fantasyCategoryPrefixes.add("Frost");
    fantasyCategoryPrefixes.add("Fire");
    fantasyCategoryPrefixes.add("Ice");
    fantasyCategoryPrefixes.add("Wind");
    fantasyCategoryPrefixes.add("Earth");
    fantasyCategoryPrefixes.add("Shadow");
    fantasyCategoryPrefixes.add("Flame");
    fantasyCategoryPrefixes.add("Sea");
    fantasyCategoryPrefixes.add("Sky");
    fantasyCategoryPrefixes.add("Night");
    fantasyCategoryPrefixes.add("Daybreak");
    fantasyCategoryPrefixes.add("Spirit");
    fantasyCategoryPrefixes.add("Mist");
    fantasyCategoryPrefixes.add("Ember");
    fantasyCategoryPrefixes.add("Aurora");
    fantasyCategoryPrefixes.add("Rain");
    fantasyCategoryPrefixes.add("Cloud");
    fantasyCategoryPrefixes.add("Crystal");
    fantasyCategoryPrefixes.add("Sparkling");

    fantasyProductNamePrefixes.add("Ultra");
    fantasyProductNamePrefixes.add("Mega");
    fantasyProductNamePrefixes.add("Super");
    fantasyProductNamePrefixes.add("Hyper");
    fantasyProductNamePrefixes.add("Power");
    fantasyProductNamePrefixes.add("Turbo");
    fantasyProductNamePrefixes.add("Max");
    fantasyProductNamePrefixes.add("Epic");
    fantasyProductNamePrefixes.add("Prime");
    fantasyProductNamePrefixes.add("Alpha");
    fantasyProductNamePrefixes.add("Omega");
    fantasyProductNamePrefixes.add("Dynamic");
    fantasyProductNamePrefixes.add("Infinite");
    fantasyProductNamePrefixes.add("Ultimate");
    fantasyProductNamePrefixes.add("Fantastic");
    fantasyProductNamePrefixes.add("Supreme");
    fantasyProductNamePrefixes.add("Master");
    fantasyProductNamePrefixes.add("Royal");
    fantasyProductNamePrefixes.add("Golden");
    fantasyProductNamePrefixes.add("Silver");
    fantasyProductNamePrefixes.add("Platinum");
    fantasyProductNamePrefixes.add("Diamond");
    fantasyProductNamePrefixes.add("Crystal");
    fantasyProductNamePrefixes.add("Magic");
    fantasyProductNamePrefixes.add("Enigma");
    fantasyProductNamePrefixes.add("Mystic");
    fantasyProductNamePrefixes.add("Spectral");
    fantasyProductNamePrefixes.add("Celestial");
    fantasyProductNamePrefixes.add("Astral");
    fantasyProductNamePrefixes.add("Cosmic");
    fantasyProductNamePrefixes.add("Ethereal");
    fantasyProductNamePrefixes.add("Dream");
    fantasyProductNamePrefixes.add("Miracle");
    fantasyProductNamePrefixes.add("Wonder");
    fantasyProductNamePrefixes.add("Star");
    fantasyProductNamePrefixes.add("Moon");
    fantasyProductNamePrefixes.add("Sun");
    fantasyProductNamePrefixes.add("Galactic");
    fantasyProductNamePrefixes.add("Neon");
    fantasyProductNamePrefixes.add("Pixel");
    fantasyProductNamePrefixes.add("Tech");
    fantasyProductNamePrefixes.add("Cyber");
    fantasyProductNamePrefixes.add("Bio");
    fantasyProductNamePrefixes.add("Nano");
    fantasyProductNamePrefixes.add("Terra");
    fantasyProductNamePrefixes.add("Vortex");
    fantasyProductNamePrefixes.add("Nova");
    fantasyProductNamePrefixes.add("Electro");

    fantasyProductNames.add("Blade");
    fantasyProductNames.add("Shield");
    fantasyProductNames.add("Potion");
    fantasyProductNames.add("Elixir");
    fantasyProductNames.add("Amulet");
    fantasyProductNames.add("Charm");
    fantasyProductNames.add("Crystal");
    fantasyProductNames.add("Gem");
    fantasyProductNames.add("Rune");
    fantasyProductNames.add("Scroll");
    fantasyProductNames.add("Relic");
    fantasyProductNames.add("Orb");
    fantasyProductNames.add("Talisman");
    fantasyProductNames.add("Staff");
    fantasyProductNames.add("Robe");
    fantasyProductNames.add("Cloak");
    fantasyProductNames.add("Helm");
    fantasyProductNames.add("Gauntlet");
    fantasyProductNames.add("Ring");
    fantasyProductNames.add("Dagger");
    fantasyProductNames.add("Bow");
    fantasyProductNames.add("Arrow");
    fantasyProductNames.add("Tome");
    fantasyProductNames.add("Wand");
    fantasyProductNames.add("Scepter");
    fantasyProductNames.add("Trident");
    fantasyProductNames.add("Horn");
    fantasyProductNames.add("Glove");
    fantasyProductNames.add("Shroud");
    fantasyProductNames.add("Mantle");
    fantasyProductNames.add("Phoenix");
    fantasyProductNames.add("Griffin");
    fantasyProductNames.add("Dragon");
    fantasyProductNames.add("Unicorn");
    fantasyProductNames.add("Phoenix");
    fantasyProductNames.add("Griffin");
    fantasyProductNames.add("Dragon");
    fantasyProductNames.add("Unicorn");
    fantasyProductNames.add("Phoenix");
    fantasyProductNames.add("Griffin");
    fantasyProductNames.add("Dragon");
    fantasyProductNames.add("Unicorn");
    fantasyProductNames.add("Phoenix");
    fantasyProductNames.add("Griffin");
    fantasyProductNames.add("Dragon");
    fantasyProductNames.add("Unicorn");
    fantasyProductNames.add("Phoenix");
    fantasyProductNames.add("Griffin");
    fantasyProductNames.add("Dragon");
    fantasyProductNames.add("Unicorn");

    fantasyProductNameSuffixes.add("-Craft");
    fantasyProductNameSuffixes.add("-Master");
    fantasyProductNameSuffixes.add("-Glow");
    fantasyProductNameSuffixes.add("-Forge");
    fantasyProductNameSuffixes.add("-Glide");
    fantasyProductNameSuffixes.add("-Grip");
    fantasyProductNameSuffixes.add("-Tech");
    fantasyProductNameSuffixes.add("-Fusion");
    fantasyProductNameSuffixes.add("-Wave");
    fantasyProductNameSuffixes.add("-Blaze");
    fantasyProductNameSuffixes.add("-Blast");
    fantasyProductNameSuffixes.add("-Storm");
    fantasyProductNameSuffixes.add("-Spark");
    fantasyProductNameSuffixes.add("-Flare");
    fantasyProductNameSuffixes.add("-Shine");
    fantasyProductNameSuffixes.add("-Whisper");
    fantasyProductNameSuffixes.add("-Charm");
    fantasyProductNameSuffixes.add("-Glimmer");
    fantasyProductNameSuffixes.add("-Strike");
    fantasyProductNameSuffixes.add("-Aura");
    fantasyProductNameSuffixes.add("-Echo");
    fantasyProductNameSuffixes.add("-Zap");
    fantasyProductNameSuffixes.add("-Whirl");
    fantasyProductNameSuffixes.add("-Crest");
    fantasyProductNameSuffixes.add("-Aegis");
    fantasyProductNameSuffixes.add("-Vortex");
    fantasyProductNameSuffixes.add("-Blade");
    fantasyProductNameSuffixes.add("-Gaze");
    fantasyProductNameSuffixes.add("-Rift");
    fantasyProductNameSuffixes.add("-Pulse");
    fantasyProductNameSuffixes.add("-Nova");
    fantasyProductNameSuffixes.add("-Saber");
    fantasyProductNameSuffixes.add("-Dream");
    fantasyProductNameSuffixes.add("-Glory");
    fantasyProductNameSuffixes.add("-Breeze");
    fantasyProductNameSuffixes.add("-Torrent");
    fantasyProductNameSuffixes.add("-Rage");
    fantasyProductNameSuffixes.add("-Spike");
    fantasyProductNameSuffixes.add("-Soul");
    fantasyProductNameSuffixes.add("-Rush");
    fantasyProductNameSuffixes.add("-Shadow");
    fantasyProductNameSuffixes.add("-Essence");
    fantasyProductNameSuffixes.add("-Wisp");
    fantasyProductNameSuffixes.add("-Oracle");
    fantasyProductNameSuffixes.add("-Quake");

    fantasyProductsDesc.add("Magic Fire Sword");
    fantasyProductsDesc.add("Invisibility Potion");
    fantasyProductsDesc.add("Enchanted Wand");
    fantasyProductsDesc.add("Lucky Charm");
    fantasyProductsDesc.add("Dark Power Ring");
    fantasyProductsDesc.add("Invisibility Cape");
    fantasyProductsDesc.add("Ancient Spell Scroll");
    fantasyProductsDesc.add("Chalice of Wisdom");
    fantasyProductsDesc.add("Flying Boots");
    fantasyProductsDesc.add("Mirror of Truth");
    fantasyProductsDesc.add("Staff of Elements");
    fantasyProductsDesc.add("Teleportation Gloves");
    fantasyProductsDesc.add("Orb of Eternity");
    fantasyProductsDesc.add("Robe of Ancient Wizards");
    fantasyProductsDesc.add("Grimoire of Forbidden Spells");
    fantasyProductsDesc.add("Empathy Necklace");
    fantasyProductsDesc.add("Destiny Spear");
    fantasyProductsDesc.add("Royal Scepter");
    fantasyProductsDesc.add("Wishing Lamp");
    fantasyProductsDesc.add("Book of Secrets");
    fantasyProductsDesc.add("Kings' Crown");
    fantasyProductsDesc.add("Enchanted Forest Flute");
    fantasyProductsDesc.add("Time Sandglass");
    fantasyProductsDesc.add("Resistance Shield");
    fantasyProductsDesc.add("Dwarven Hammer");
    fantasyProductsDesc.add("Precision Bow");
    fantasyProductsDesc.add("Cauldron of Transformations");
    fantasyProductsDesc.add("Sphere of Knowledge");
    fantasyProductsDesc.add("Invisibility Hood");
    fantasyProductsDesc.add("Elder Staff");
    fantasyProductsDesc.add("Fortune Chain");
    fantasyProductsDesc.add("Barbarian Axe");
    fantasyProductsDesc.add("Dark Mage Robe");
    fantasyProductsDesc.add("Protection Bracelet");
    fantasyProductsDesc.add("Bottomless Bag");
    fantasyProductsDesc.add("Angel Wings");
    fantasyProductsDesc.add("Wisdom Tome");
    fantasyProductsDesc.add("Stealth Dagger");
    fantasyProductsDesc.add("Treasure Chest");
    fantasyProductsDesc.add("Luck Star");
    fantasyProductsDesc.add("Dragon Heart");
    fantasyProductsDesc.add("Life Gem");
    fantasyProductsDesc.add("Eternity Sundial");
    fantasyProductsDesc.add("Brave Shield");
    fantasyProductsDesc.add("Invincible Armor");
    fantasyProductsDesc.add("Youth Bottle");
    fantasyProductsDesc.add("Destiny Ring");

    fantasyProductPrices.add(50.99);
    fantasyProductPrices.add(30.50);
    fantasyProductPrices.add(20.75);
    fantasyProductPrices.add(15.99);
    fantasyProductPrices.add(45.25);
    fantasyProductPrices.add(35.75);
    fantasyProductPrices.add(60.00);
    fantasyProductPrices.add(75.50);
    fantasyProductPrices.add(55.25);
    fantasyProductPrices.add(40.99);
    fantasyProductPrices.add(70.25);
    fantasyProductPrices.add(25.50);
    fantasyProductPrices.add(80.75);
    fantasyProductPrices.add(65.99);
    fantasyProductPrices.add(90.50);
    fantasyProductPrices.add(100.25);
    fantasyProductPrices.add(110.99);
    fantasyProductPrices.add(95.50);
    fantasyProductPrices.add(120.75);
    fantasyProductPrices.add(130.99);
    fantasyProductPrices.add(140.25);
    fantasyProductPrices.add(150.50);
    fantasyProductPrices.add(160.75);
    fantasyProductPrices.add(170.99);
    fantasyProductPrices.add(180.25);
    fantasyProductPrices.add(190.50);
    fantasyProductPrices.add(200.75);
    fantasyProductPrices.add(210.99);
    fantasyProductPrices.add(220.25);
    fantasyProductPrices.add(230.50);
    fantasyProductPrices.add(240.75);
    fantasyProductPrices.add(250.99);
    fantasyProductPrices.add(260.25);
    fantasyProductPrices.add(270.50);
    fantasyProductPrices.add(280.75);
    fantasyProductPrices.add(290.99);
    fantasyProductPrices.add(300.25);
    fantasyProductPrices.add(310.50);
    fantasyProductPrices.add(320.75);
    fantasyProductPrices.add(330.99);
    fantasyProductPrices.add(340.25);
    fantasyProductPrices.add(350.50);
    fantasyProductPrices.add(360.75);
    fantasyProductPrices.add(370.99);
    fantasyProductPrices.add(380.25);
    fantasyProductPrices.add(390.50);
    fantasyProductPrices.add(400.75);

    fantasyEmailProviders.add("@gmail.com");
    fantasyEmailProviders.add("@yahoo.com");
    fantasyEmailProviders.add("@outlook.com");
    fantasyEmailProviders.add("@aol.com");
    fantasyEmailProviders.add("@zoho.com");
    fantasyEmailProviders.add("@protonmail.com");
    fantasyEmailProviders.add("@icloud.com");
    fantasyEmailProviders.add("@gmx.com");
    fantasyEmailProviders.add("@yandex.com");
    fantasyEmailProviders.add("@mail.com");
    fantasyEmailProviders.add("@tutanota.com");
    fantasyEmailProviders.add("@fastmail.com");
    fantasyEmailProviders.add("@hey.com");
    fantasyEmailProviders.add("@hushmail.com");
    fantasyEmailProviders.add("@runbox.com");
    fantasyEmailProviders.add("@posteo.net");
    fantasyEmailProviders.add("@mailfence.com");
    fantasyEmailProviders.add("@startmail.com");
    fantasyEmailProviders.add("@mail.ru");
    fantasyEmailProviders.add("@lycos.com");
    fantasyEmailProviders.add("@rediffmail.com");
    fantasyEmailProviders.add("@in.com");
    fantasyEmailProviders.add("@mail2world.com");
    fantasyEmailProviders.add("@pobox.com");
    fantasyEmailProviders.add("@inbox.com");
    fantasyEmailProviders.add("@gawab.com");
    fantasyEmailProviders.add("@10minutemail.com");
    fantasyEmailProviders.add("@guerrillamail.com");
    fantasyEmailProviders.add("@mailinator.com");
    fantasyEmailProviders.add("@yopmail.com");
    fantasyEmailProviders.add("@temp-mail.org");
    fantasyEmailProviders.add("@maildrop.cc");
    fantasyEmailProviders.add("@trashmail.com");
    fantasyEmailProviders.add("@dispostable.com");
    fantasyEmailProviders.add("@getnada.com");
    fantasyEmailProviders.add("@mintemail.com");
    fantasyEmailProviders.add("@mohmal.com");
    fantasyEmailProviders.add("@throwawaymail.com");
    fantasyEmailProviders.add("@mytrashmail.com");
    fantasyEmailProviders.add("@spamgourmet.com");
    fantasyEmailProviders.add("@jetable.org");
    fantasyEmailProviders.add("@mailcatch.com");
    fantasyEmailProviders.add("@tempinbox.com");
    fantasyEmailProviders.add("@spamfree24.org");
    fantasyEmailProviders.add("@instantemailaddress.com");
    fantasyEmailProviders.add("@e4ward.com");
    fantasyEmailProviders.add("@meltmail.com");
    fantasyEmailProviders.add("@sharklasers.com");
    fantasyEmailProviders.add("@mailnesia.com");

    fantasyOrderStatuses.add("Pending");
    fantasyOrderStatuses.add("Processing");
    fantasyOrderStatuses.add("Shipped");
    fantasyOrderStatuses.add("Delivered");
    fantasyOrderStatuses.add("Cancelled");
    fantasyOrderStatuses.add("Returned");
    fantasyOrderStatuses.add("Refunded");
    fantasyOrderStatuses.add("On Hold");
    fantasyOrderStatuses.add("Completed");
    fantasyOrderStatuses.add("Failed");
    fantasyOrderStatuses.add("Awaiting Payment");
    fantasyOrderStatuses.add("Awaiting Fulfillment");
    fantasyOrderStatuses.add("Awaiting Shipment");
    fantasyOrderStatuses.add("Awaiting Pickup");
    fantasyOrderStatuses.add("Partially Shipped");
    fantasyOrderStatuses.add("Partially Refunded");
    fantasyOrderStatuses.add("Declined");
    fantasyOrderStatuses.add("Backordered");
    fantasyOrderStatuses.add("In Production");
    fantasyOrderStatuses.add("Customs Clearance");
    fantasyOrderStatuses.add("Out for Delivery");
    fantasyOrderStatuses.add("Delivered to Pickup Point");
    fantasyOrderStatuses.add("Pick Up Failed");
    fantasyOrderStatuses.add("Rescheduled");
    fantasyOrderStatuses.add("Payment Review");
    fantasyOrderStatuses.add("Order Confirmed");
    fantasyOrderStatuses.add("Order Packed");
    fantasyOrderStatuses.add("Label Created");
    fantasyOrderStatuses.add("Awaiting Inventory");
    fantasyOrderStatuses.add("Pending Authorization");
    fantasyOrderStatuses.add("Payment Accepted");
    fantasyOrderStatuses.add("Payment Declined");
    fantasyOrderStatuses.add("Awaiting Return");
    fantasyOrderStatuses.add("Return Received");
    fantasyOrderStatuses.add("Exchanged");
    fantasyOrderStatuses.add("Replaced");
    fantasyOrderStatuses.add("Lost in Transit");
    fantasyOrderStatuses.add("Damaged in Transit");
    fantasyOrderStatuses.add("Awaiting Customer Response");
    fantasyOrderStatuses.add("Customer Notified");
    fantasyOrderStatuses.add("Delivery Scheduled");
    fantasyOrderStatuses.add("Delivery Attempted");
    fantasyOrderStatuses.add("Unable to Deliver");
    fantasyOrderStatuses.add("Address Issue");
    fantasyOrderStatuses.add("Wrong Item Delivered");
    fantasyOrderStatuses.add("Out of Stock");
    fantasyOrderStatuses.add("Re-routed");
    fantasyOrderStatuses.add("Disputed");
    fantasyOrderStatuses.add("Pending Verification");
    fantasyOrderStatuses.add("Awaiting Customs Clearance");

    fantasyPaymentMethods.add("Credit Card");
    fantasyPaymentMethods.add("Debit Card");
    fantasyPaymentMethods.add("PayPal");
    fantasyPaymentMethods.add("Bank Transfer");
    fantasyPaymentMethods.add("Cash on Delivery");
    fantasyPaymentMethods.add("Google Pay");
    fantasyPaymentMethods.add("Apple Pay");
    fantasyPaymentMethods.add("Amazon Pay");
    fantasyPaymentMethods.add("Bitcoin");
    fantasyPaymentMethods.add("Ethereum");
    fantasyPaymentMethods.add("Stripe");
    fantasyPaymentMethods.add("Square");
    fantasyPaymentMethods.add("Alipay");
    fantasyPaymentMethods.add("WeChat Pay");
    fantasyPaymentMethods.add("Venmo");
    fantasyPaymentMethods.add("Zelle");
    fantasyPaymentMethods.add("Skrill");
    fantasyPaymentMethods.add("Neteller");
    fantasyPaymentMethods.add("Payoneer");
    fantasyPaymentMethods.add("Paysafecard");
    fantasyPaymentMethods.add("Discover");
    fantasyPaymentMethods.add("American Express");
    fantasyPaymentMethods.add("JCB");
    fantasyPaymentMethods.add("UnionPay");
    fantasyPaymentMethods.add("Diners Club");
    fantasyPaymentMethods.add("Mobile Payment");
    fantasyPaymentMethods.add("Contactless Payment");
    fantasyPaymentMethods.add("Prepaid Card");
    fantasyPaymentMethods.add("Gift Card");
    fantasyPaymentMethods.add("Money Order");
    fantasyPaymentMethods.add("Personal Check");
    fantasyPaymentMethods.add("Certified Check");
    fantasyPaymentMethods.add("Direct Debit");
    fantasyPaymentMethods.add("ACH Transfer");
    fantasyPaymentMethods.add("eCheck");
    fantasyPaymentMethods.add("Bank Draft");
    fantasyPaymentMethods.add("Bill Me Later");
    fantasyPaymentMethods.add("Klarna");
    fantasyPaymentMethods.add("Afterpay");
    fantasyPaymentMethods.add("Layaway");
    fantasyPaymentMethods.add("Cryptocurrency");
    fantasyPaymentMethods.add("Cash App");
    fantasyPaymentMethods.add("Facebook Pay");
    fantasyPaymentMethods.add("Samsung Pay");
    fantasyPaymentMethods.add("Chase Pay");
    fantasyPaymentMethods.add("Wire Transfer");
    fantasyPaymentMethods.add("Postepay");
    fantasyPaymentMethods.add("SEPA Direct Debit");
    fantasyPaymentMethods.add("Sofort");
    fantasyPaymentMethods.add("Giropay");

    fantasyTransactionTypes.add("Retail Sale");
    fantasyTransactionTypes.add("Wholesale Sale");
    fantasyTransactionTypes.add("Online Sale");
    fantasyTransactionTypes.add("In-Store Sale");
    fantasyTransactionTypes.add("Phone Sale");
    fantasyTransactionTypes.add("Mail Order Sale");
    fantasyTransactionTypes.add("Business-to-Business Sale");
    fantasyTransactionTypes.add("Business-to-Consumer Sale");
    fantasyTransactionTypes.add("Subscription Sale");
    fantasyTransactionTypes.add("Auction Sale");
    fantasyTransactionTypes.add("Consignment Sale");
    fantasyTransactionTypes.add("Trade-In");
    fantasyTransactionTypes.add("Direct Sale");
    fantasyTransactionTypes.add("Channel Sale");
    fantasyTransactionTypes.add("Dropshipping Sale");
    fantasyTransactionTypes.add("Flash Sale");
    fantasyTransactionTypes.add("Seasonal Sale");
    fantasyTransactionTypes.add("Promotional Sale");
    fantasyTransactionTypes.add("Pre-Order Sale");
    fantasyTransactionTypes.add("Clearance Sale");
    fantasyTransactionTypes.add("Liquidation Sale");
    fantasyTransactionTypes.add("Rental");
    fantasyTransactionTypes.add("Lease");
    fantasyTransactionTypes.add("Installment Sale");
    fantasyTransactionTypes.add("Credit Sale");
    fantasyTransactionTypes.add("Cash Sale");
    fantasyTransactionTypes.add("Contract Sale");
    fantasyTransactionTypes.add("Export Sale");
    fantasyTransactionTypes.add("Import Sale");
    fantasyTransactionTypes.add("Government Sale");
    fantasyTransactionTypes.add("International Sale");
    fantasyTransactionTypes.add("Local Sale");
    fantasyTransactionTypes.add("Regional Sale");
    fantasyTransactionTypes.add("National Sale");
    fantasyTransactionTypes.add("Exclusive Sale");
    fantasyTransactionTypes.add("Non-Exclusive Sale");
    fantasyTransactionTypes.add("Bulk Sale");
    fantasyTransactionTypes.add("Small Quantity Sale");
    fantasyTransactionTypes.add("Subscription Renewal");
    fantasyTransactionTypes.add("One-Time Sale");
    fantasyTransactionTypes.add("Membership Sale");
    fantasyTransactionTypes.add("Service Sale");
    fantasyTransactionTypes.add("Product Sale");
    fantasyTransactionTypes.add("Digital Product Sale");
    fantasyTransactionTypes.add("Physical Product Sale");
    fantasyTransactionTypes.add("E-commerce Sale");
    fantasyTransactionTypes.add("Direct Mail Sale");
    fantasyTransactionTypes.add("Affiliate Sale");
    fantasyTransactionTypes.add("Partnership Sale");
  }

  private void sendMessage(final String origin, final String elementType, final Object object) {
    headers.put("operation", "Creation");
    headers.put("elementType", elementType);
    headers.put("correlationId", UUID.randomUUID().toString());
    headers.put("spanId", UUID.randomUUID().toString());
    headers.put("origin", origin);


    try {
      eventsService.sentEvents(Event.builder()
          .headers(headers)
          .payload(Base64.getEncoder()
              .encodeToString(objectMapper.writeValueAsString(object).getBytes()))
          .destinations(randomEventsEx)
          .build());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }


}
